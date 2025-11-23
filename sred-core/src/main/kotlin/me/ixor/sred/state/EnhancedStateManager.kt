package me.ixor.sred.state

import me.ixor.sred.core.*
import me.ixor.sred.persistence.ExtendedStatePersistence
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.Duration
import java.util.UUID

/**
 * 增强的状态管理器 - 集成所有新功能
 * 
 * 集成：
 * - 状态锁机制
 * - 轨迹追踪
 * - SQLite持久化
 * - 事务支持
 */
class EnhancedStateManager(
    override val stateRegistry: StateRegistry,
    persistence: ExtendedStatePersistence,
    private val stateLock: StateLock = StateLockFactory.create(),
    private val traceCollector: TraceCollector = TraceCollectorFactory.create()
) : StateManager {
    
    override val statePersistence: StatePersistence = persistence
    
    override var currentContext: StateContext? = null
        private set
    
    private val extendedPersistence = persistence
    
    private val stateHistory = mutableListOf<StateHistoryEntry>()
    private val mutex = Mutex()
    private val statistics = StateManagerStatisticsImpl()
    private var isInitialized = false
    private var isRunning = false
    
    // 当前轨迹
    private var currentTrace: StateTrace? = null
    private var currentTraceId: TraceId? = null
    
    override suspend fun initialize() {
        mutex.withLock {
            if (isInitialized) return
            
            isInitialized = true
        }
    }
    
    override suspend fun start() {
        mutex.withLock {
            if (!isInitialized) {
                initialize()
            }
            
            if (isRunning) return
            
            isRunning = true
            
            // 启动轨迹追踪
            currentContext?.let { context ->
                startTraceForContext(context)
            }
        }
    }
    
    override suspend fun stop() {
        mutex.withLock {
            if (!isRunning) return
            
            // 保存当前上下文
            currentContext?.let { context ->
                saveContext(context)
                finishTrace()
            }
            
            isRunning = false
        }
    }
    
    /**
     * 为上下文启动轨迹追踪
     */
    private suspend fun startTraceForContext(context: StateContext) {
        val trace = traceCollector.startTrace(context.id)
        currentTrace = trace
        currentTraceId = trace.traceId
        
        // 记录初始状态进入
        context.currentStateId?.let { stateId ->
            addTraceEntry(StateEnterEntry(
                stateId = stateId,
                contextId = context.id
            ))
        }
    }
    
    /**
     * 完成轨迹追踪
     */
    private suspend fun finishTrace() {
        currentTraceId?.let { traceId ->
            traceCollector.finishTrace(traceId)
        }
        currentTrace = null
        currentTraceId = null
    }
    
    /**
     * 添加轨迹条目
     */
    private suspend fun addTraceEntry(entry: TraceEntry) {
        currentTraceId?.let { traceId ->
            traceCollector.addTraceEntry(traceId, entry)
        }
    }
    
    override suspend fun setCurrentState(stateId: StateId, context: StateContext): StateTransitionResult {
        mutex.withLock {
            if (!isRunning) {
                throw IllegalStateException("StateManager is not running")
            }
            
            val entityId = context.id
            
            // 尝试获取状态锁
            val lockAcquired = stateLock.tryLock(entityId, stateId, Duration.ofSeconds(30))
            if (!lockAcquired) {
                return StateTransitionResult(
                    success = false,
                    updatedContext = context,
                    error = IllegalStateException("Failed to acquire lock for state $stateId")
                )
            }
            
            val oldStateId = currentContext?.currentStateId
            
            try {
                val state = stateRegistry.getState(stateId)
                    ?: return StateTransitionResult(
                        success = false,
                        updatedContext = context,
                        error = IllegalArgumentException("State $stateId not found")
                    )
                
                val startTime = System.currentTimeMillis()
                
                // 记录状态退出
                val currentState = getCurrentState()
                if (currentState != null && oldStateId != null) {
                    addTraceEntry(StateExitEntry(
                        stateId = oldStateId,
                        contextId = context.id,
                        duration = null // 可以计算持续时间
                    ))
                }
                
                try {
                    // 退出当前状态
                    val exitContext = if (currentState != null) {
                        currentState.onExit(context)
                    } else {
                        context
                    }
                    
                    // 检查是否可以进入新状态
                    if (!state.canEnter(exitContext)) {
                        return StateTransitionResult(
                            success = false,
                            updatedContext = exitContext,
                            error = IllegalStateException("Cannot enter state $stateId")
                        )
                    }
                    
                    // 记录状态进入
                    addTraceEntry(StateEnterEntry(
                        stateId = stateId,
                        contextId = context.id,
                        triggeredByEvent = null
                    ))
                    
                    // 进入新状态
                    val lastEvent = context.recentEvents.lastOrNull()
                    val newContext = if (lastEvent != null) {
                        state.onEnter(exitContext)
                            .copy(currentStateId = stateId)
                            .addEvent(lastEvent)
                    } else {
                        state.onEnter(exitContext)
                            .copy(currentStateId = stateId)
                    }
                    
                    // 更新当前状态和上下文
                    currentContext = newContext
                    
                    // 保存到持久化存储
                    saveContext(newContext)
                    
                    // 保存状态历史
                    val historyEntry = StateHistoryEntry(
                        timestamp = Instant.now(),
                        fromStateId = oldStateId,
                        toStateId = stateId,
                        event = newContext.recentEvents.lastOrNull(),
                        contextId = newContext.id
                    )
                    stateHistory.add(historyEntry)
                    
                    // 保存到数据库
                    newContext.recentEvents.lastOrNull()?.let { event ->
                        extendedPersistence.saveEvent(newContext.id, event)
                    }
                    extendedPersistence.saveStateHistory(
                        newContext.id,
                        oldStateId,
                        stateId,
                        newContext.recentEvents.lastOrNull()?.id
                    )
                    
                    // 更新统计信息
                    statistics.recordTransition(System.currentTimeMillis() - startTime)
                    
                    return StateTransitionResult(
                        success = true,
                        nextStateId = stateId,
                        updatedContext = newContext
                    )
                    
                } catch (e: Exception) {
                    statistics.recordError()
                    // 记录错误到轨迹（使用一个简单的错误条目）
                    // 由于TraceEntry是sealed class，这里暂时跳过详细记录
                    return StateTransitionResult(
                        success = false,
                        updatedContext = context,
                        error = e
                    )
                }
            } finally {
                // 释放旧状态的锁
                oldStateId?.let { stateLock.unlock(entityId, it) }
            }
        }
    }
    
    override fun getCurrentState(): State? {
        val context = currentContext ?: return null
        return stateRegistry.getState(context.currentStateId ?: return null)
    }
    
    override suspend fun transitionTo(
        targetStateId: StateId,
        event: Event,
        context: StateContext
    ): StateTransitionResult {
        // 记录事件接收
        addTraceEntry(EventReceivedEntry(
            event = event,
            currentStateId = context.currentStateId
        ))
        
        val currentState = getCurrentState()
            ?: return StateTransitionResult(
                success = false,
                updatedContext = context,
                error = IllegalStateException("No current state")
            )
        
        // 检查当前状态是否可以处理此事件
        if (!currentState.canHandle(event, context)) {
            return StateTransitionResult(
                success = false,
                updatedContext = context,
                error = IllegalStateException("Current state ${currentState.id} cannot handle event ${event.id}")
            )
        }
        
        // 记录转移开始（暂时跳过，因为TraceEntry是sealed class）
        
        val startTime = System.currentTimeMillis()
        
        try {
            // 处理事件
            val transitionResult = currentState.handleEvent(event, context.addEvent(event))
            
            if (transitionResult.success && transitionResult.nextStateId != null) {
                // 执行状态转移
                val result = setCurrentState(transitionResult.nextStateId, transitionResult.updatedContext)
                
                // 记录转移完成（暂时跳过，因为TraceEntry是sealed class）
                
                return result
            }
            
            return transitionResult
        } catch (e: Exception) {
            // 记录转移失败（暂时跳过）
            return StateTransitionResult(
                success = false,
                updatedContext = context,
                error = e
            )
        }
    }
    
    override suspend fun saveContext(context: StateContext) {
        statePersistence.saveContext(context)
    }
    
    override suspend fun restoreContext(contextId: ContextId): StateContext? {
        val context = statePersistence.loadContext(contextId)
        if (context != null && isRunning) {
            currentContext = context
            startTraceForContext(context)
        }
        return context
    }
    
    override fun getStateHistory(): List<StateHistoryEntry> {
        val contextId = currentContext?.id
        return if (contextId != null) {
            runBlocking {
                extendedPersistence.getStateHistory(contextId)
            }
        } else {
            stateHistory.toList()
        }
    }
    
    override fun getStatistics(): StateManagerStatistics {
        // 注意：使用 runBlocking 是因为接口是同步的，但内部统计使用 Mutex（需要 suspend）
        // 这可能会阻塞调用线程，但通常统计方法调用频率较低，可以接受
        return runBlocking { statistics.getStatistics() }
    }
    
    /**
     * 获取当前轨迹
     */
    fun getCurrentTrace(): StateTrace? = currentTrace
    
    /**
     * 获取轨迹追踪器
     */
    fun getTraceCollector(): TraceCollector = traceCollector
    
    /**
     * 获取状态锁
     */
    fun getStateLock(): StateLock = stateLock
}

/**
 * 增强的状态管理器工厂
 */
object EnhancedStateManagerFactory {
    fun create(
        stateRegistry: StateRegistry = StateRegistryFactory.create(),
        dbPath: String = "sred_state.db",
        stateLock: StateLock = StateLockFactory.create(),
        traceCollector: TraceCollector = TraceCollectorFactory.create()
    ): EnhancedStateManager {
        val persistence = me.ixor.sred.persistence.PersistenceAdapterFactory.createSqliteAdapter(dbPath)
        runBlocking {
            persistence.initialize()
        }
        return EnhancedStateManager(stateRegistry, persistence, stateLock, traceCollector)
    }
}

