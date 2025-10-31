package me.ixor.sred.orchestrator

import me.ixor.sred.core.*
import me.ixor.sred.event.*
import me.ixor.sred.state.*
import me.ixor.sred.persistence.SqliteStatePersistence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * StateOrchestrator构建器 - 支持链式配置
 * 
 * 使用构建器模式简化StateOrchestrator的创建和配置
 */
class StateOrchestratorBuilder {
    private var stateRegistry: StateRegistry? = null
    private var statePersistence: StatePersistence? = null
    private var stateLock: StateLock? = null
    private var traceCollector: TraceCollector? = null
    private var eventBus: EventBus? = null
    private var transitionRegistry: TransitionRegistry? = null
    private var temporalEventScheduler: TemporalEventScheduler? = null
    private var enableTemporalEvents: Boolean = true
    private var maxConcurrentTransactions: Int = 10
    private var persistenceDbPath: String = "sred_state.db"
    
    /**
     * 设置状态注册表
     */
    fun withStateRegistry(registry: StateRegistry) = apply {
        this.stateRegistry = registry
    }
    
    /**
     * 设置状态持久化
     */
    fun withStatePersistence(persistence: StatePersistence) = apply {
        this.statePersistence = persistence
    }
    
    /**
     * 使用SQLite持久化
     */
    fun withSqlitePersistence(dbPath: String = "sred_state.db") = apply {
        this.persistenceDbPath = dbPath
        this.statePersistence = SqliteStatePersistence(dbPath)
    }
    
    /**
     * 设置状态锁
     */
    fun withStateLock(lock: StateLock) = apply {
        this.stateLock = lock
    }
    
    /**
     * 设置轨迹收集器
     */
    fun withTraceCollector(collector: TraceCollector) = apply {
        this.traceCollector = collector
    }
    
    /**
     * 设置事件总线
     */
    fun withEventBus(bus: EventBus) = apply {
        this.eventBus = bus
    }
    
    /**
     * 设置转移注册表
     */
    fun withTransitionRegistry(registry: TransitionRegistry) = apply {
        this.transitionRegistry = registry
    }
    
    /**
     * 设置时间事件调度器
     */
    fun withTemporalEventScheduler(scheduler: TemporalEventScheduler) = apply {
        this.temporalEventScheduler = scheduler
    }
    
    /**
     * 启用/禁用时间事件
     */
    fun enableTemporalEvents(enable: Boolean) = apply {
        this.enableTemporalEvents = enable
    }
    
    /**
     * 设置最大并发事务数
     */
    fun withMaxConcurrentTransactions(max: Int) = apply {
        this.maxConcurrentTransactions = max
    }
    
    /**
     * 构建StateOrchestrator
     */
    suspend fun build(): StateOrchestrator {
        // 创建默认组件（如果未提供）
        val registry = stateRegistry ?: StateRegistryFactory.create()
        val persistence = statePersistence ?: SqliteStatePersistence(persistenceDbPath)
        val lock = stateLock ?: StateLockFactory.create()
        val collector = traceCollector ?: TraceCollectorFactory.create()
        val bus = eventBus ?: EventBusFactory.create()
        val transRegistry = transitionRegistry ?: TransitionRegistryImpl()
        
        // 创建StateManager（需要SqliteStatePersistence）
        val sqlitePersistence = if (persistence is SqliteStatePersistence) {
            persistence
        } else {
            SqliteStatePersistence(persistenceDbPath)
        }
        
        val stateManager = EnhancedStateManager(
            stateRegistry = registry,
            persistence = sqlitePersistence,
            stateLock = lock,
            traceCollector = collector
        )
        
        // 创建时间事件调度器（如果需要）
        val scheduler = if (enableTemporalEvents && temporalEventScheduler == null) {
            TemporalEventSchedulerFactory.create(bus)
        } else {
            temporalEventScheduler
        }
        
        // 创建并初始化StateManager
        stateManager.initialize()
        
        // 返回EnhancedStateOrchestrator
        return EnhancedStateOrchestrator(
            stateManager = stateManager,
            eventBus = bus,
            transitionRegistry = transRegistry,
            temporalEventScheduler = scheduler,
            maxConcurrentTransactions = maxConcurrentTransactions
        )
    }
    
    /**
     * 创建默认配置的构建器
     */
    companion object {
        fun create(): StateOrchestratorBuilder = StateOrchestratorBuilder()
    }
}

/**
 * 扩展函数：快速创建Orchestrator
 */
suspend fun StateOrchestratorBuilder.buildAndStart(): StateOrchestrator {
    val orchestrator = build()
    orchestrator.start()
    return orchestrator
}

