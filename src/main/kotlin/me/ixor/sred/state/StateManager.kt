package me.ixor.sred.state

import me.ixor.sred.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 状态管理器 - SRED架构的状态层核心组件
 * 
 * 状态管理器负责：
 * 1. 状态的生命周期管理
 * 2. 状态上下文的持久化与恢复
 * 3. 状态转移的协调
 * 4. 状态一致性维护
 */
interface StateManager {
    /**
     * 状态注册表
     */
    val stateRegistry: StateRegistry
    
    /**
     * 状态持久化器
     */
    val statePersistence: StatePersistence
    
    /**
     * 当前上下文
     */
    val currentContext: StateContext?
    
    /**
     * 初始化状态管理器
     */
    suspend fun initialize()
    
    /**
     * 启动状态管理器
     */
    suspend fun start()
    
    /**
     * 停止状态管理器
     */
    suspend fun stop()
    
    /**
     * 设置当前状态
     */
    suspend fun setCurrentState(stateId: StateId, context: StateContext): StateTransitionResult
    
    /**
     * 获取当前状态
     */
    fun getCurrentState(): State?
    
    /**
     * 执行状态转移
     */
    suspend fun transitionTo(
        targetStateId: StateId,
        event: Event,
        context: StateContext
    ): StateTransitionResult
    
    /**
     * 保存状态上下文
     */
    suspend fun saveContext(context: StateContext)
    
    /**
     * 恢复状态上下文
     */
    suspend fun restoreContext(contextId: ContextId): StateContext?
    
    /**
     * 获取状态历史
     */
    fun getStateHistory(): List<StateHistoryEntry>
    
    /**
     * 获取管理器统计信息
     */
    fun getStatistics(): StateManagerStatistics
}

/**
 * 状态持久化接口
 */
interface StatePersistence {
    /**
     * 保存状态上下文
     */
    suspend fun saveContext(context: StateContext)
    
    /**
     * 加载状态上下文
     */
    suspend fun loadContext(contextId: ContextId): StateContext?
    
    /**
     * 删除状态上下文
     */
    suspend fun deleteContext(contextId: ContextId)
    
    /**
     * 列出所有上下文ID
     */
    suspend fun listContextIds(): List<ContextId>
}

/**
 * 状态历史条目
 */
data class StateHistoryEntry(
    val timestamp: Instant,
    val fromStateId: StateId?,
    val toStateId: StateId,
    val event: Event?,
    val contextId: ContextId
)

/**
 * 状态管理器统计信息
 */
data class StateManagerStatistics(
    val totalTransitions: Long,
    val currentStateId: StateId?,
    val contextCount: Int,
    val lastTransitionAt: Instant?,
    val averageTransitionTimeMs: Double
)

/**
 * 状态管理器实现
 */
class StateManagerImpl(
    override val stateRegistry: StateRegistry,
    override val statePersistence: StatePersistence
) : StateManager {
    
    override var currentContext: StateContext? = null
        private set
    
    private val stateHistory = mutableListOf<StateHistoryEntry>()
    private val mutex = Mutex()
    private val statistics = StateManagerStatisticsImpl()
    private var isInitialized = false
    private var isRunning = false
    
    override suspend fun initialize() {
        mutex.withLock {
            if (isInitialized) return
            
            // 初始化状态注册表
            // 这里可以加载预定义的状态
            
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
        }
    }
    
    override suspend fun stop() {
        mutex.withLock {
            if (!isRunning) return
            
            // 保存当前上下文
            currentContext?.let { context ->
                saveContext(context)
            }
            
            isRunning = false
        }
    }
    
    override suspend fun setCurrentState(stateId: StateId, context: StateContext): StateTransitionResult {
        mutex.withLock {
            if (!isRunning) {
                throw IllegalStateException("StateManager is not running")
            }
            
            val state = stateRegistry.getState(stateId)
                ?: return StateTransitionResult(
                    success = false,
                    updatedContext = context,
                    error = IllegalArgumentException("State $stateId not found")
                )
            
            val startTime = System.currentTimeMillis()
            
            try {
                // 退出当前状态
                val currentState = getCurrentState()
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
                
                // 进入新状态
                val newContext = state.onEnter(exitContext)
                    .copy(currentStateId = stateId)
                
                // 更新当前状态和上下文
                currentContext = newContext
                
                // 记录状态历史
                val historyEntry = StateHistoryEntry(
                    timestamp = Instant.now(),
                    fromStateId = currentState?.id,
                    toStateId = stateId,
                    event = null,
                    contextId = newContext.id
                )
                stateHistory.add(historyEntry)
                
                // 更新统计信息
                statistics.recordTransition(System.currentTimeMillis() - startTime)
                
                return StateTransitionResult(
                    success = true,
                    nextStateId = stateId,
                    updatedContext = newContext
                )
                
            } catch (e: Exception) {
                statistics.recordError()
                return StateTransitionResult(
                    success = false,
                    updatedContext = context,
                    error = e
                )
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
        
        // 处理事件
        val transitionResult = currentState.handleEvent(event, context)
        
        if (transitionResult.success && transitionResult.nextStateId != null) {
            // 执行状态转移
            return setCurrentState(transitionResult.nextStateId, transitionResult.updatedContext)
        }
        
        return transitionResult
    }
    
    override suspend fun saveContext(context: StateContext) {
        statePersistence.saveContext(context)
    }
    
    override suspend fun restoreContext(contextId: ContextId): StateContext? {
        return statePersistence.loadContext(contextId)
    }
    
    override fun getStateHistory(): List<StateHistoryEntry> {
        return stateHistory.toList()
    }
    
    override fun getStatistics(): StateManagerStatistics = runBlocking { statistics.getStatistics() }
}

/**
 * 内存状态持久化实现
 */
class InMemoryStatePersistence : StatePersistence {
    private val contexts = ConcurrentHashMap<ContextId, StateContext>()
    
    override suspend fun saveContext(context: StateContext) {
        contexts[context.id] = context
    }
    
    override suspend fun loadContext(contextId: ContextId): StateContext? {
        return contexts[contextId]
    }
    
    override suspend fun deleteContext(contextId: ContextId) {
        contexts.remove(contextId)
    }
    
    override suspend fun listContextIds(): List<ContextId> {
        return contexts.keys.toList()
    }
}

/**
 * 状态管理器统计信息实现
 */
private class StateManagerStatisticsImpl {
    private var totalTransitions = 0L
    private var errorCount = 0L
    private var totalTransitionTime = 0L
    private var transitionCount = 0L
    private var lastTransitionAt: Instant? = null
    private val mutex = Mutex()
    
    suspend fun recordTransition(timeMs: Long) {
        mutex.withLock {
            totalTransitions++
            totalTransitionTime += timeMs
            transitionCount++
            lastTransitionAt = Instant.now()
        }
    }
    
    suspend fun recordError() {
        mutex.withLock {
            errorCount++
        }
    }
    
    suspend fun getStatistics(): StateManagerStatistics {
        return mutex.withLock {
            StateManagerStatistics(
                totalTransitions = totalTransitions,
                currentStateId = null, // Will be set by StateManager
                contextCount = 0, // Will be set by StateManager
                lastTransitionAt = lastTransitionAt,
                averageTransitionTimeMs = if (transitionCount > 0) {
                    totalTransitionTime.toDouble() / transitionCount
                } else 0.0
            )
        }
    }
}

/**
 * 状态管理器工厂
 */
object StateManagerFactory {
    fun create(
        stateRegistry: StateRegistry = StateRegistryFactory.create(),
        statePersistence: StatePersistence = InMemoryStatePersistence()
    ): StateManager = StateManagerImpl(stateRegistry, statePersistence)
}
