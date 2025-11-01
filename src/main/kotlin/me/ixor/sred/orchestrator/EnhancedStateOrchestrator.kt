package me.ixor.sred.orchestrator

import me.ixor.sred.core.*
import me.ixor.sred.event.*
import me.ixor.sred.state.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 增强的状态调度器 - 集成事务和并发控制
 * 
 * 集成：
 * - 事务事件支持
 * - 增强的并发控制
 * - 时间事件调度
 */
class EnhancedStateOrchestrator(
    override val stateManager: StateManager,
    override val eventBus: EventBus,
    override val transitionRegistry: TransitionRegistry,
    private val temporalEventScheduler: TemporalEventScheduler? = null,
    private val maxConcurrentTransactions: Int = 10
) : StateOrchestrator {
    
    private val mutex = Mutex()
    private val statistics = OrchestratorStatisticsImpl()
    private val transactionMutex = Mutex()
    private val activeTransactions = ConcurrentHashMap<TransactionId, TransactionEvent>()
    private val transactionSemaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrentTransactions)
    
    private var isRunning = false
    private var eventListener: EventListener? = null
    
    override suspend fun start() {
        mutex.withLock {
            if (isRunning) return
            
            // 启动状态管理器
            stateManager.start()
            
            // 启动事件总线
            eventBus.start()
            
            // 启动时间事件调度器
            temporalEventScheduler?.start()
            
            // 注册事件监听器
            eventListener = createEventListener()
            eventBus.subscribe(
                eventType = EventType("system", "all"),
                listener = eventListener!!,
                filter = null
            )
            
            isRunning = true
        }
    }
    
    override suspend fun stop() {
        mutex.withLock {
            if (!isRunning) return
            
            // 停止时间事件调度器
            temporalEventScheduler?.stop()
            
            // 停止事件总线
            eventBus.stop()
            
            // 停止状态管理器
            stateManager.stop()
            
            isRunning = false
        }
    }
    
    override suspend fun processEvent(event: Event): OrchestrationResult {
        if (!isRunning) {
            return OrchestrationResult(
                success = false,
                error = IllegalStateException("Orchestrator is not running")
            )
        }
        
        // 处理时间事件
        if (event is TemporalEvent) {
            return processTemporalEvent(event)
        }
        
        val startTime = System.currentTimeMillis()
        
        try {
            val currentState = stateManager.getCurrentState()
            val currentContext = stateManager.currentContext
            
            if (currentState == null || currentContext == null) {
                return OrchestrationResult(
                    success = false,
                    error = IllegalStateException("No current state or context")
                )
            }
            
            // 查找可能的转移
            val possibleTransitions = transitionRegistry.findTransitions(
                currentState.id,
                event.type
            )
            
            if (possibleTransitions.isEmpty()) {
                return OrchestrationResult(
                    success = false,
                    error = IllegalStateException("No transitions found for state ${currentState.id} and event ${event.type}")
                )
            }
            
            // 选择最佳转移（按优先级排序）
            val bestTransition = possibleTransitions
                .filter { it.canTransition(currentState, event, currentContext) }
                .maxByOrNull { it.priority }
            
            if (bestTransition == null) {
                return OrchestrationResult(
                    success = false,
                    error = IllegalStateException("No valid transitions found")
                )
            }
            
            // 执行状态转移（使用互斥锁确保原子性）
            val transitionResult = mutex.withLock {
                bestTransition.executeTransition(
                    currentState,
                    event,
                    currentContext
                )
            }
            
            if (transitionResult.success && transitionResult.nextStateId != null) {
                // 执行状态转移
                val stateTransitionResult = stateManager.transitionTo(
                    transitionResult.nextStateId,
                    event,
                    transitionResult.updatedContext
                )
                
                if (stateTransitionResult.success) {
                    statistics.recordSuccess()
                    return OrchestrationResult(
                        success = true,
                        nextStateId = stateTransitionResult.nextStateId,
                        updatedContext = stateTransitionResult.updatedContext,
                        sideEffects = stateTransitionResult.sideEffects
                    )
                } else {
                    statistics.recordFailure()
                    return OrchestrationResult(
                        success = false,
                        error = stateTransitionResult.error
                    )
                }
            } else {
                statistics.recordFailure()
                return OrchestrationResult(
                    success = false,
                    error = transitionResult.error
                )
            }
            
        } catch (e: Exception) {
            statistics.recordFailure()
            return OrchestrationResult(
                success = false,
                error = e
            )
        } finally {
            statistics.updateProcessingTime(System.currentTimeMillis() - startTime)
        }
    }
    
    /**
     * 处理时间事件
     */
    private suspend fun processTemporalEvent(event: TemporalEvent): OrchestrationResult {
        return when (event.temporalType) {
            EventTemporalType.SYNCHRONOUS -> {
                // 同步事件，立即处理
                processEvent(event)
            }
            EventTemporalType.ASYNCHRONOUS -> {
                // 异步事件，在后台处理
                CoroutineScope(Dispatchers.Default).launch {
                    processEvent(event)
                }
                OrchestrationResult(
                    success = true,
                    updatedContext = stateManager.currentContext
                )
            }
            EventTemporalType.DEFERRED -> {
                // 延迟事件，应该已经通过调度器在正确时间发布
                processEvent(event)
            }
            EventTemporalType.PERIODIC -> {
                // 周期事件，应该已经通过调度器在正确时间发布
                processEvent(event)
            }
        }
    }
    
    /**
     * 处理事务事件
     */
    suspend fun processTransaction(transaction: TransactionEvent): TransactionResult {
        // 获取事务信号量
        transactionSemaphore.acquire()
        
        return try {
            activeTransactions[transaction.transactionId] = transaction
            
            val result = transaction.execute()
            
            if (!result.success) {
                // 执行失败，触发补偿
                transaction.rollback()
            }
            
            result
        } finally {
            activeTransactions.remove(transaction.transactionId)
            transactionSemaphore.release()
        }
    }
    
    override suspend fun registerTransition(transition: StateTransition) {
        transitionRegistry.registerTransition(transition)
    }
    
    override suspend fun unregisterTransition(transitionId: TransitionId) {
        transitionRegistry.unregisterTransition(transitionId)
    }
    
    override fun getStatistics(): OrchestratorStatistics = runBlocking { statistics.getStatistics() }
    
    /**
     * 获取时间事件调度器
     */
    fun getTemporalEventScheduler(): TemporalEventScheduler? = temporalEventScheduler
    
    /**
     * 调度延迟事件
     */
    suspend fun scheduleDeferred(event: Event, delay: java.time.Duration) {
        temporalEventScheduler?.scheduleDeferred(event, delay)
    }
    
    /**
     * 调度周期事件
     */
    suspend fun schedulePeriodic(
        event: Event,
        period: java.time.Duration,
        startTime: Instant = Instant.now(),
        endTime: Instant? = null
    ): String? {
        return temporalEventScheduler?.schedulePeriodic(event, period, startTime, endTime)
    }
    
    private fun createEventListener(): EventListener {
        return object : EventListener {
            override val id: String = "enhanced-orchestrator-listener"
            
            override suspend fun onEvent(event: Event) {
                processEvent(event)
            }
            
            override suspend fun onError(event: Event, error: Throwable) {
                // Log error
                statistics.recordFailure()
            }
        }
    }
}

/**
 * 增强的状态调度器工厂
 */
object EnhancedStateOrchestratorFactory {
    fun create(
        stateManager: StateManager,
        eventBus: EventBus,
        transitionRegistry: TransitionRegistry = TransitionRegistryImpl(),
        enableTemporalEvents: Boolean = true,
        maxConcurrentTransactions: Int = 10
    ): EnhancedStateOrchestrator {
        val scheduler = if (enableTemporalEvents) {
            TemporalEventSchedulerFactory.create(eventBus)
        } else {
            null
        }
        
        return EnhancedStateOrchestrator(
            stateManager = stateManager,
            eventBus = eventBus,
            transitionRegistry = transitionRegistry,
            temporalEventScheduler = scheduler,
            maxConcurrentTransactions = maxConcurrentTransactions
        )
    }
}

