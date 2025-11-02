package me.ixor.sred

import me.ixor.sred.core.*
import me.ixor.sred.declarative.*
import me.ixor.sred.declarative.format.FormatLoader
import me.ixor.sred.event.*
import me.ixor.sred.orchestrator.*
import me.ixor.sred.persistence.ExtendedStatePersistence
import me.ixor.sred.persistence.PersistenceAdapterFactory
import me.ixor.sred.core.logger
import kotlinx.coroutines.*
import java.io.Closeable

/**
 * SRED 统一入口 - 提供简单易用的 API
 * 
 * 设计理念：外简内繁
 * - 外部接口简单直观，降低使用门槛
 * - 内部实现高内聚、高扩展性
 */
object SRED {
    private val log = logger<SRED>()
    
    /**
     * 创建状态机引擎构建器
     */
    fun engine(): EngineBuilder = EngineBuilder()
    
    /**
     * 从配置文件快速创建状态机引擎
     */
    suspend fun fromConfig(
        configPath: String,
        dbPath: String? = null,
        handlers: Any? = null
    ): SREDEngine {
        return engine()
            .config(configPath)
            .apply { if (dbPath != null) persistence(dbPath) }
            .apply { if (handlers != null) handlers(handlers) }
            .build()
    }
}

/**
 * SRED 引擎构建器 - 提供流畅的 DSL
 */
class EngineBuilder {
    private var configPath: String? = null
    private var stateFlow: StateFlow? = null
    private var dbPath: String? = null
    private var persistence: ExtendedStatePersistence? = null
    private var handlers: MutableList<Any> = mutableListOf()
    private var orchestratorBuilder: StateOrchestratorBuilder? = null
    private var autoStart: Boolean = true
    
    /**
     * 从配置文件加载状态流定义
     */
    fun config(path: String) = apply {
        this.configPath = path
    }
    
    /**
     * 使用自定义状态流
     */
    fun stateFlow(flow: StateFlow) = apply {
        this.stateFlow = flow
    }
    
    /**
     * 配置持久化存储（SQLite）
     */
    fun persistence(dbPath: String) = apply {
        this.dbPath = dbPath
    }
    
    /**
     * 使用自定义持久化适配器
     */
    fun persistence(persistence: ExtendedStatePersistence) = apply {
        this.persistence = persistence
    }
    
    /**
     * 注册状态处理器（支持多个）
     */
    fun handlers(vararg handlerInstances: Any) = apply {
        this.handlers.addAll(handlerInstances)
    }
    
    /**
     * 注册状态处理器（单个）
     */
    fun handlers(handlerInstance: Any) = apply {
        this.handlers.add(handlerInstance)
    }
    
    /**
     * 自定义编排器配置
     */
    fun orchestrator(configure: StateOrchestratorBuilder.() -> Unit) = apply {
        if (orchestratorBuilder == null) {
            orchestratorBuilder = StateOrchestratorBuilder.create()
        }
        orchestratorBuilder!!.configure()
    }
    
    /**
     * 设置是否自动启动
     */
    fun autoStart(autoStart: Boolean) = apply {
        this.autoStart = autoStart
    }
    
    /**
     * 构建 SRED 引擎
     */
    suspend fun build(): SREDEngine {
        // 1. 加载或使用状态流
        val flow = if (stateFlow != null) {
            stateFlow!!
        } else if (configPath != null) {
            FormatLoader.load(configPath!!)
        } else {
            throw IllegalArgumentException("必须提供 configPath 或 stateFlow")
        }
        
        // 2. 绑定处理器
        handlers.forEach { handlerInstance ->
            flow.bindAnnotatedFunctions(handlerInstance)
        }
        
        // 3. 构建状态机
        val stateMachine = flow.build()
        
        // 4. 创建持久化适配器
        val dbPathValue = dbPath  // 避免 smart cast 问题
        val persistenceAdapter = persistence ?: if (dbPathValue != null) {
            PersistenceAdapterFactory.createSqliteAdapter(dbPathValue)
                .also { it.initialize() }
        } else {
            null
        }
        
        // 5. 构建编排器（如果不需要编排器，可以设置为 null）
        val orchestrator = if (orchestratorBuilder != null) {
            orchestratorBuilder!!
                .apply { if (persistenceAdapter != null) withExtendedStatePersistence(persistenceAdapter) }
                .build()
        } else {
            // 创建简单的编排器用于演示
            // 实际使用中，用户可以通过 orchestrator {} DSL 自定义
            StateOrchestratorBuilder.create()
                .apply { if (persistenceAdapter != null) withExtendedStatePersistence(persistenceAdapter) }
                .build()
        }
        
        // 6. 启动编排器（如果需要）
        if (autoStart) {
            orchestrator.start()
        }
        
        return SREDEngine(stateMachine, orchestrator, persistenceAdapter, handlers.toMutableList())
    }
}

/**
 * SRED 引擎 - 封装状态机和编排器的使用
 * 支持多流程管理和动态刷新
 */
class SREDEngine(
    private var stateMachine: StateMachine,
    private val orchestrator: StateOrchestrator,
    private val persistence: ExtendedStatePersistence?,
    private val handlers: MutableList<Any>
) : Closeable {
    private val log = logger<SREDEngine>()
    private val instances = mutableMapOf<String, StateMachineInstance>()
    private val workflows = mutableMapOf<String, StateMachine>() // 流程ID -> 状态机
    private var currentWorkflowId: String = "default"
    
    init {
        workflows[currentWorkflowId] = stateMachine
    }
    
    /**
     * 启动状态机实例（使用当前流程）
     */
    suspend fun start(
        instanceId: String,
        initialData: Map<String, Any> = emptyMap(),
        workflowId: String? = null
    ): StateMachineInstance {
        val workflow = workflowId?.let { workflows[it] } ?: stateMachine
        val context = StateContextFactory.builder()
            .id(instanceId)
            .apply {
                initialData.forEach { (key, value) ->
                    localState(key, value)
                }
            }
            .build()
        
        // 保存初始上下文（包含流程ID）
        persistence?.saveContext(context.copy(
            metadata = context.metadata + mapOf("workflowId" to (workflowId ?: currentWorkflowId))
        ))
        
        val instance = workflow.start(instanceId, context)
        instances[instanceId] = instance
        
        log.debug { "状态机实例已启动: $instanceId, 流程: ${workflowId ?: currentWorkflowId}" }
        return instance
    }
    
    /**
     * 自动执行状态流转直到完成（到达终态或错误状态）
     * 支持从持久化存储恢复实例（支持无限长时间停顿）
     */
    suspend fun runUntilComplete(
        instanceId: String,
        eventType: String = "process",
        eventName: String = "自动处理",
        onStateChange: ((from: String?, to: String) -> Unit)? = null,
        onComplete: ((finalState: String, context: StateContext) -> Unit)? = null,
        onError: ((error: Throwable) -> Unit)? = null
    ): String? {
        // 确保实例已加载（从持久化恢复，如果不在内存中）
        ensureInstanceLoaded(instanceId)
        
        var currentState = getCurrentState(instanceId)
        val finalStates = setOf("success", "completed", "failed", "error")
        val statePattern = Regex(".*_(success|completed|failed|error)$")
        
        while (currentState != null) {
            // 检查是否到达终态
            val isFinalState = currentState in finalStates || 
                              statePattern.matches(currentState) ||
                              currentState.contains("_success") ||
                              currentState.contains("_failed")
            
            if (isFinalState) {
                val context = getContext(instanceId)
                if (context != null) {
                    onComplete?.invoke(currentState, context)
                }
                return currentState
            }
            
            val previousState = currentState
            // process 方法会自动从持久化恢复实例
            val result = process(instanceId, eventType, eventName)
            
            // 每次处理事件后都会保存状态，即使服务器重启也能恢复
            currentState = getCurrentState(instanceId)
            
            if (currentState != previousState && currentState != null) {
                onStateChange?.invoke(previousState, currentState)
                
                // 检查新状态是否需要暂停（基于配置）
                val stateDef = stateMachine.getStateDefinition(currentState)
                if (stateDef?.pauseOnEnter == true) {
                    log.info { "状态 ${currentState} 配置为自动暂停，工作流已暂停" }
                    return currentState  // 暂停执行，等待外部触发
                }
            }
            
            if (!result.success) {
                onError?.invoke(result.error ?: Exception("处理失败"))
                return currentState
            }
            
            delay(100) // 短暂延迟
        }
        
        return currentState
    }
    
    /**
     * 加载实例（从持久化存储恢复，如果不在内存中）
     * 支持无限长时间停顿后的恢复（即使服务器重启也能恢复）
     */
    suspend fun loadInstance(instanceId: String): StateMachineInstance? {
        // 如果已在内存中，直接返回
        if (instances.containsKey(instanceId)) {
            return instances[instanceId]
        }
        
        // 从持久化存储加载上下文
        val context = persistence?.loadContext(instanceId) ?: return null
        val workflowId = context.metadata["workflowId"] as? String ?: currentWorkflowId
        val workflow = workflows[workflowId] ?: stateMachine
        
        // 恢复实例状态
        try {
            // 使用 restore 方法恢复已存在的实例状态
            // 如果 restore 失败（实例不存在），则使用 start 创建新实例
            workflow.restore(instanceId, context)
            val instance = StateMachineInstance(workflow, instanceId)
            instances[instanceId] = instance
            log.debug { "从持久化存储恢复实例: $instanceId, 状态: ${context.currentStateId}, 流程: $workflowId" }
            return instance
        } catch (e: Exception) {
            // 如果 restore 失败，尝试用 start（可能是新实例）
            try {
                val instance = workflow.start(instanceId, context)
                instances[instanceId] = instance
                log.debug { "创建新实例: $instanceId, 状态: ${context.currentStateId}" }
                return instance
            } catch (e2: Exception) {
                log.warn(e2) { "无法恢复或创建实例: $instanceId" }
                return null
            }
        }
    }
    
    /**
     * 获取实例对应的状态机（用于多流程场景）
     * 注意：这是一个同步方法，如果实例不在内存中，将使用当前流程
     */
    private fun getInstanceMachineSync(instanceId: String): StateMachine {
        // 先从内存中的实例获取状态机
        instances[instanceId]?.getMachine()?.let { return it }
        
        // 如果不在内存中，无法同步加载，返回当前流程的状态机
        // 在需要时，调用者应确保实例已加载
        return stateMachine
    }
    
    /**
     * 获取实例对应的状态机（用于多流程场景，支持从持久化加载）
     */
    private suspend fun getInstanceMachine(instanceId: String): StateMachine {
        // 先从内存中的实例获取状态机
        instances[instanceId]?.getMachine()?.let { return it }
        
        // 如果不在内存中，从持久化存储加载上下文获取流程ID
        val persistedContext = persistence?.loadContext(instanceId)
        val workflowId = persistedContext?.metadata?.get("workflowId") as? String ?: currentWorkflowId
        
        return workflows[workflowId] ?: stateMachine
    }
    
    /**
     * 确保实例已加载（从持久化存储恢复，如果不在内存中）
     */
    private suspend fun ensureInstanceLoaded(instanceId: String): StateMachineInstance? {
        return instances[instanceId] ?: loadInstance(instanceId)
    }
    
    /**
     * 处理事件（自动保存状态）
     * 支持从持久化存储自动恢复实例
     */
    suspend fun process(
        instanceId: String,
        eventType: String,
        eventName: String = eventType,
        payload: Map<String, Any> = emptyMap()
    ): StateResult {
        // 尝试加载实例（可能从持久化存储恢复）
        val instance = instances[instanceId] ?: loadInstance(instanceId)
            ?: throw IllegalStateException("实例 $instanceId 不存在，请先调用 start()")
        
        val event = EventFactory.builder()
            .type(eventType.split(".").getOrElse(0) { eventType }, eventType.split(".").getOrElse(1) { "" })
            .name(eventName)
            .apply {
                payload.forEach { (key, value) ->
                    this.payload(key, value)
                }
            }
            .build()
        
        // 保存事件
        persistence?.saveEvent(instanceId, event)
        
        // 处理事件
        val result = instance.processEvent(event)
        
        // 保存更新后的上下文和状态历史
        val currentState = instance.getCurrentState()
        val context = instance.getContext()
        if (context != null && currentState != null) {
            // 检查是否是状态转移（从暂停状态恢复）
            val previousContext = persistence?.loadContext(instanceId)
            val previousState = previousContext?.currentStateId
            val wasPaused = previousContext?.metadata?.containsKey("_pausedAt") == true
            
            // 如果从暂停状态转移出来，清除暂停标记
            val shouldClearPause = wasPaused && previousState != currentState
            val updatedMetadata = if (shouldClearPause) {
                context.metadata - "_pausedAt" - "_pausedState" - "_pauseTimeout"
            } else {
                context.metadata
            }
            
            // 注意：清除暂停标记后，需要在 WorkflowExecutor 中同步移除 pausedInstances
            // 这需要在外部调用时处理（例如在 API 服务中）
            
            val updatedContext = context.copy(metadata = updatedMetadata)
            persistence?.saveContext(updatedContext)
            
            if (previousState != currentState) {
                persistence?.saveStateHistory(
                    contextId = instanceId,
                    fromStateId = previousState,
                    toStateId = currentState,
                    eventId = event.id,
                    timestamp = event.timestamp
                )
            }
        }
        
        return result
    }
    
    /**
     * 更新上下文的 metadata
     */
    suspend fun updateMetadata(instanceId: String, metadata: Map<String, Any>) {
        val context = getContext(instanceId) ?: return
        val updatedContext = context.copy(metadata = context.metadata + metadata)
        persistence?.saveContext(updatedContext)
        
        // 更新内存中的实例上下文
        instances[instanceId]?.let { instance ->
            val stateMachine = stateMachine // 获取状态机
            // 更新状态机内部的上下文（需要通过状态机方法）
            // 这里需要在 StateMachine 中添加更新上下文的方法
        }
    }
    
    /**
     * 获取当前状态（支持从持久化存储加载）
     * 用于支持无限长时间停顿的实例状态查询
     */
    suspend fun getCurrentState(instanceId: String): String? {
        // 先从内存查找
        instances[instanceId]?.getCurrentState()?.let { return it }
        
        // 如果不在内存中，从持久化存储加载
        val context = persistence?.loadContext(instanceId)
        return context?.currentStateId
    }
    
    /**
     * 获取上下文（支持从持久化存储加载）
     * 用于支持无限长时间停顿的实例上下文查询
     */
    suspend fun getContext(instanceId: String): StateContext? {
        // 先从内存查找
        instances[instanceId]?.getContext()?.let { return it }
        
        // 如果不在内存中，从持久化存储加载
        return persistence?.loadContext(instanceId)
    }
    
    /**
     * 获取上下文数据（类型安全）
     */
    fun <T> getData(instanceId: String, key: String, clazz: Class<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return instances[instanceId]?.getContext()?.getLocalState<Any>(key) as? T
    }
    
    /**
     * 获取上下文数据（类型安全，使用类型推断）
     * 支持从持久化存储加载
     * 注意：使用 noinline 避免 inline 函数访问非 public API 的限制
     */
    suspend fun <T> getDataTyped(instanceId: String, key: String): T? {
        // 先从内存查找
        instances[instanceId]?.getContext()?.getLocalState<Any>(key)?.let {
            @Suppress("UNCHECKED_CAST")
            return it as? T
        }
        
        // 如果不在内存中，从持久化存储加载
        val context = getContext(instanceId)
        @Suppress("UNCHECKED_CAST")
        return context?.getLocalState<Any>(key) as? T
    }
    
    /**
     * 停止实例
     */
    suspend fun stop(instanceId: String) {
        instances.remove(instanceId)
        log.debug { "状态机实例已停止: $instanceId" }
    }
    
    /**
     * 停止所有实例和编排器
     */
    suspend fun stopAll() {
        instances.clear()
        orchestrator.stop()
        log.info { "所有状态机实例和编排器已停止" }
    }
    
    /**
     * 关闭资源
     */
    override fun close() {
        runBlocking {
            stopAll()
        }
        // 如果持久化适配器实现了 Closeable，则关闭
        (persistence as? java.io.Closeable)?.close()
    }
    
    /**
     * 获取状态机
     */
    fun getStateMachine(): StateMachine = stateMachine
    
    /**
     * 获取编排器
     */
    fun getOrchestrator(): StateOrchestrator = orchestrator
    
    /**
     * 获取持久化适配器
     */
    fun getPersistence(): ExtendedStatePersistence? = persistence
    
    /**
     * 直接转移状态（不触发状态函数，用于超时等特殊情况）
     * 
     * @param instanceId 实例ID
     * @param targetStateId 目标状态ID
     * @param reason 转移原因
     * @param workflowId 流程ID（可选，如果未指定则使用当前流程）
     */
    suspend fun forceTransition(
        instanceId: String, 
        targetStateId: String, 
        reason: String = "force",
        workflowId: String? = null
    ) {
        // 确保实例已加载
        ensureInstanceLoaded(instanceId)
        
        // 确定使用的流程
        val context = getContext(instanceId)
        val actualWorkflowId = workflowId ?: (context?.metadata["workflowId"] as? String) ?: currentWorkflowId
        val targetMachine = if (actualWorkflowId != currentWorkflowId) {
            workflows[actualWorkflowId] ?: throw IllegalStateException("Workflow '$actualWorkflowId' not found")
        } else {
            stateMachine
        }
        
        val workflow = targetMachine.getFlow()
        
        // 验证目标状态存在
        val targetState = workflow.states[targetStateId]
            ?: throw IllegalStateException("Target state '$targetStateId' not found in workflow '$actualWorkflowId'")
        
        val currentState = getCurrentState(instanceId)
            ?: throw IllegalStateException("Instance not found: $instanceId")
        
        // 直接转移状态（使用正确的状态机）
        targetMachine.forceTransition(instanceId, targetStateId, reason)
        
        // 更新持久化存储
        val updatedContext = getContext(instanceId)
        if (updatedContext != null) {
            persistence?.saveContext(updatedContext)
            
            // 保存状态历史
            persistence?.saveStateHistory(
                contextId = instanceId,
                fromStateId = currentState,
                toStateId = targetStateId,
                eventId = null,  // 强制转移没有事件
                timestamp = java.time.Instant.now()
            )
        }
        
        log.info { "强制转移状态: $instanceId, $currentState -> $targetStateId (原因: $reason, 流程: $actualWorkflowId)" }
    }
    
    /**
     * 注册新流程
     */
    suspend fun registerWorkflow(
        workflowId: String,
        configPath: String? = null,
        stateFlow: StateFlow? = null,
        additionalHandlers: List<Any> = emptyList()
    ): StateMachine {
        if (workflows.containsKey(workflowId)) {
            throw IllegalArgumentException("流程 $workflowId 已存在")
        }
        
        val flow = stateFlow ?: if (configPath != null) {
            FormatLoader.load(configPath)
        } else {
            throw IllegalArgumentException("必须提供 configPath 或 stateFlow")
        }
        
        // 绑定处理器
        (handlers + additionalHandlers).forEach { handlerInstance ->
            flow.bindAnnotatedFunctions(handlerInstance)
        }
        
        val machine = flow.build()
        workflows[workflowId] = machine
        
        log.info { "流程已注册: $workflowId" }
        return machine
    }
    
    /**
     * 刷新/更新流程定义
     */
    suspend fun refreshWorkflow(
        workflowId: String,
        configPath: String? = null,
        stateFlow: StateFlow? = null,
        additionalHandlers: List<Any> = emptyList()
    ): StateMachine {
        val flow = stateFlow ?: if (configPath != null) {
            FormatLoader.load(configPath)
        } else {
            throw IllegalArgumentException("必须提供 configPath 或 stateFlow")
        }
        
        // 绑定处理器
        (handlers + additionalHandlers).forEach { handlerInstance ->
            flow.bindAnnotatedFunctions(handlerInstance)
        }
        
        val machine = flow.build()
        workflows[workflowId] = machine
        
        // 如果刷新的是当前流程，更新当前状态机
        if (workflowId == currentWorkflowId) {
            stateMachine = machine
        }
        
        log.info { "流程已刷新: $workflowId" }
        return machine
    }
    
    /**
     * 切换当前流程
     */
    fun switchWorkflow(workflowId: String) {
        val workflow = workflows[workflowId] 
            ?: throw IllegalArgumentException("流程 $workflowId 不存在")
        currentWorkflowId = workflowId
        stateMachine = workflow
        log.info { "已切换到流程: $workflowId" }
    }
    
    /**
     * 获取所有流程ID
     */
    fun getWorkflowIds(): Set<String> = workflows.keys.toSet()
    
    /**
     * 获取当前流程ID
     */
    fun getCurrentWorkflowId(): String = currentWorkflowId
    
    /**
     * 获取指定流程的状态机
     */
    fun getWorkflow(workflowId: String): StateMachine? = workflows[workflowId]
    
    /**
     * 添加处理器（动态）
     */
    fun addHandler(handler: Any) {
        handlers.add(handler)
        log.debug { "已添加处理器: ${handler::class.simpleName}" }
    }
}

/**
 * DSL 函数：快速创建 SRED 引擎
 */
suspend fun sred(block: EngineBuilder.() -> Unit): SREDEngine {
    return SRED.engine().apply(block).build()
}

/**
 * DSL 函数：从配置文件快速创建
 */
suspend fun sredFromConfig(
    configPath: String,
    dbPath: String? = null,
    handlers: Any? = null
): SREDEngine = SRED.fromConfig(configPath, dbPath, handlers)

/**
 * 工作流执行器 DSL - 最简洁的使用方式
 */
class WorkflowRunner(
    private val engine: SREDEngine,
    private val instanceId: String
) {
    suspend fun execute(
        eventType: String = "process",
        eventName: String = "执行",
        onStateChange: ((from: String?, to: String) -> Unit)? = null
    ): String? = engine.runUntilComplete(instanceId, eventType, eventName, onStateChange)
    
    suspend fun <T> data(key: String): T? = engine.getDataTyped(instanceId, key)
    suspend fun state(): String? = engine.getCurrentState(instanceId)
}

/**
 * 在引擎上执行工作流的扩展函数 - 最简洁的API
 */
suspend fun SREDEngine.runWorkflow(
    initialData: Map<String, Any> = emptyMap(),
    instanceId: String = "workflow_${System.currentTimeMillis()}",
    eventType: String = "process",
    eventName: String = "执行",
    onStateChange: ((from: String?, to: String) -> Unit)? = null,
    onComplete: ((state: String, context: StateContext) -> Unit)? = null,
    onError: ((error: Throwable) -> Unit)? = null
): WorkflowResult {
    start(instanceId, initialData)
    val finalState = runUntilComplete(
        instanceId, eventType, eventName,
        onStateChange, onComplete, onError
    )
    return WorkflowResult(instanceId, finalState, this)
}

/**
 * 工作流执行结果
 * 支持从持久化存储查询状态（支持无限长时间停顿）
 */
data class WorkflowResult(
    val instanceId: String,
    val finalState: String?,
    private val engine: SREDEngine
) {
    suspend fun <T> data(key: String): T? = engine.getDataTyped(instanceId, key)
    suspend fun state(): String? = engine.getCurrentState(instanceId)
    suspend fun context(): StateContext? = engine.getContext(instanceId)
    suspend fun stop() = engine.stop(instanceId)
}

