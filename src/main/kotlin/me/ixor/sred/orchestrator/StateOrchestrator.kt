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
 * 状态调度器 - SRED架构的控制论中枢
 * 
 * 调度器是整个SRED架构的"控制论中枢"。
 * 它基于全局上下文决定何时触发何种状态转移。
 * 
 * 核心逻辑：NextState = T(CurrentState, Event, Context)
 */
interface StateOrchestrator {
    /**
     * 状态管理器
     */
    val stateManager: StateManager
    
    /**
     * 事件总线
     */
    val eventBus: EventBus
    
    /**
     * 状态转移注册表
     */
    val transitionRegistry: TransitionRegistry
    
    /**
     * 启动调度器
     */
    suspend fun start()
    
    /**
     * 停止调度器
     */
    suspend fun stop()
    
    /**
     * 处理事件
     */
    suspend fun processEvent(event: Event): OrchestrationResult
    
    /**
     * 注册状态转移
     */
    suspend fun registerTransition(transition: StateTransition)
    
    /**
     * 注销状态转移
     */
    suspend fun unregisterTransition(transitionId: TransitionId)
    
    /**
     * 获取调度器统计信息
     */
    fun getStatistics(): OrchestratorStatistics
}

/**
 * 状态转移注册表
 */
interface TransitionRegistry {
    /**
     * 注册状态转移
     */
    suspend fun registerTransition(transition: StateTransition)
    
    /**
     * 注销状态转移
     */
    suspend fun unregisterTransition(transitionId: TransitionId)
    
    /**
     * 获取状态转移
     */
    fun getTransition(transitionId: TransitionId): StateTransition?
    
    /**
     * 获取所有状态转移
     */
    fun getAllTransitions(): Collection<StateTransition>
    
    /**
     * 根据源状态和事件类型查找转移
     */
    fun findTransitions(fromStateId: StateId, eventType: EventType): List<StateTransition>
}

/**
 * 调度结果
 */
data class OrchestrationResult(
    val success: Boolean,
    val nextStateId: StateId? = null,
    val updatedContext: StateContext? = null,
    val error: Throwable? = null,
    val sideEffects: List<SideEffect> = emptyList()
)

/**
 * 调度器统计信息
 */
data class OrchestratorStatistics(
    val totalEventsProcessed: Long,
    val successfulTransitions: Long,
    val failedTransitions: Long,
    val averageProcessingTimeMs: Double,
    val lastProcessedAt: Instant?
)

/**
 * 状态调度器实现
 */
class StateOrchestratorImpl(
    override val stateManager: StateManager,
    override val eventBus: EventBus,
    override val transitionRegistry: TransitionRegistry
) : StateOrchestrator {
    
    private val mutex = Mutex()
    private val statistics = OrchestratorStatisticsImpl()
    private var isRunning = false
    private var eventListener: EventListener? = null
    
    override suspend fun start() {
        mutex.withLock {
            if (isRunning) return
            
            // 启动状态管理器
            stateManager.start()
            
            // 启动事件总线
            eventBus.start()
            
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
            
            // 执行状态转移
            val transitionResult = bestTransition.executeTransition(
                currentState,
                event,
                currentContext
            )
            
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
    
    override suspend fun registerTransition(transition: StateTransition) {
        transitionRegistry.registerTransition(transition)
    }
    
    override suspend fun unregisterTransition(transitionId: TransitionId) {
        transitionRegistry.unregisterTransition(transitionId)
    }
    
    override fun getStatistics(): OrchestratorStatistics = runBlocking { statistics.getStatistics() }
    
    private fun createEventListener(): EventListener {
        return object : EventListener {
            override val id: String = "orchestrator-listener"
            
            override suspend fun onEvent(event: Event) {
                processEvent(event)
            }
            
            override suspend fun onError(event: Event, error: Throwable) {
                // Log error
            }
        }
    }
}

/**
 * 状态转移注册表实现
 */
class TransitionRegistryImpl : TransitionRegistry {
    private val transitions = ConcurrentHashMap<TransitionId, StateTransition>()
    private val transitionsBySource = ConcurrentHashMap<StateId, MutableList<StateTransition>>()
    private val transitionsByEventType = ConcurrentHashMap<EventType, MutableList<StateTransition>>()
    
    override suspend fun registerTransition(transition: StateTransition) {
        transitions[transition.id] = transition
        
        transitionsBySource.computeIfAbsent(transition.fromStateId) { mutableListOf() }
            .add(transition)
        
        transitionsByEventType.computeIfAbsent(transition.eventType) { mutableListOf() }
            .add(transition)
    }
    
    override suspend fun unregisterTransition(transitionId: TransitionId) {
        val transition = transitions.remove(transitionId) ?: return
        
        transitionsBySource[transition.fromStateId]?.remove(transition)
        transitionsByEventType[transition.eventType]?.remove(transition)
    }
    
    override fun getTransition(transitionId: TransitionId): StateTransition? {
        return transitions[transitionId]
    }
    
    override fun getAllTransitions(): Collection<StateTransition> {
        return transitions.values
    }
    
    override fun findTransitions(fromStateId: StateId, eventType: EventType): List<StateTransition> {
        val sourceTransitions = transitionsBySource[fromStateId] ?: emptyList()
        val eventTransitions = transitionsByEventType[eventType] ?: emptyList()
        
        return sourceTransitions.intersect(eventTransitions.toSet()).toList()
    }
}

/**
 * 调度器统计信息实现
 */
internal class OrchestratorStatisticsImpl {
    private var totalEventsProcessed = 0L
    private var successfulTransitions = 0L
    private var failedTransitions = 0L
    private var totalProcessingTime = 0L
    private var processingCount = 0L
    private var lastProcessedAt: Instant? = null
    private val mutex = Mutex()
    
    suspend fun recordSuccess() {
        mutex.withLock {
            successfulTransitions++
            lastProcessedAt = Instant.now()
        }
    }
    
    suspend fun recordFailure() {
        mutex.withLock {
            failedTransitions++
            lastProcessedAt = Instant.now()
        }
    }
    
    suspend fun updateProcessingTime(timeMs: Long) {
        mutex.withLock {
            totalEventsProcessed++
            totalProcessingTime += timeMs
            processingCount++
        }
    }
    
    suspend fun getStatistics(): OrchestratorStatistics {
        return mutex.withLock {
            OrchestratorStatistics(
                totalEventsProcessed = totalEventsProcessed,
                successfulTransitions = successfulTransitions,
                failedTransitions = failedTransitions,
                averageProcessingTimeMs = if (processingCount > 0) {
                    totalProcessingTime.toDouble() / processingCount
                } else 0.0,
                lastProcessedAt = lastProcessedAt
            )
        }
    }
}

/**
 * 状态调度器工厂
 */
object StateOrchestratorFactory {
    fun create(
        stateManager: StateManager,
        eventBus: EventBus,
        transitionRegistry: TransitionRegistry = TransitionRegistryImpl()
    ): StateOrchestrator = StateOrchestratorImpl(stateManager, eventBus, transitionRegistry)
}
