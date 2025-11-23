package me.ixor.sred.api.services

import kotlinx.coroutines.*
import me.ixor.sred.*
import me.ixor.sred.core.logger
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 工作流执行器 - 管理异步执行的长时间工作流
 * 支持基于配置的长时间停顿和超时控制
 */
@Component
class WorkflowExecutor {
    private val log = logger<WorkflowExecutor>()
    private val executionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val runningInstances = mutableMapOf<String, Job>()
    
    /**
     * 状态暂停信息
     */
    data class PauseInfo(
        val instanceId: String,
        val stateId: String,
        val pausedAt: Long,
        val timeout: Long?,  // null 表示不超时，-1 表示无限久
        val engineId: String  // 所属引擎ID，用于超时检查时匹配引擎
    )
    
    /**
     * 暂停实例信息（用于查询 API）
     */
    data class PausedInstanceInfo(
        val instanceId: String,
        val stateId: String,
        val pausedAt: Long,
        val timeout: Long?,
        val elapsed: Long  // 已暂停的秒数
    )
    
    private val pausedInstances = ConcurrentHashMap<String, PauseInfo>()
    
    /**
     * 超时监控任务
     */
    private var timeoutMonitorJob: Job? = null
    
    init {
        startTimeoutMonitor()
    }
    
    /**
     * 存储引擎引用（用于超时处理）
     */
    private val engines = ConcurrentHashMap<String, SREDEngine>()
    
    /**
     * 引擎ID到实例ID前缀的映射（用于匹配实例和引擎）
     */
    private val engineInstancePrefixes = ConcurrentHashMap<String, String>()
    
    /**
     * 注册引擎（用于超时处理）
     */
    fun registerEngine(engineId: String, engine: SREDEngine, instanceIdPrefix: String? = null) {
        engines[engineId] = engine
        instanceIdPrefix?.let { prefix ->
            engineInstancePrefixes[engineId] = prefix
        }
    }
    
    /**
     * 根据实例ID查找所属引擎ID
     */
    private fun findEngineId(instanceId: String): String? {
        // 先通过前缀匹配
        engineInstancePrefixes.entries.forEach { (engineId, prefix) ->
            if (instanceId.startsWith(prefix)) {
                return engineId
            }
        }
        // 如果没有匹配，返回默认引擎（如果有的话）
        return engines.keys.firstOrNull()
    }
    
    /**
     * 启动超时监控任务
     */
    private fun startTimeoutMonitor() {
        timeoutMonitorJob = executionScope.launch {
            while (isActive) {
                try {
                    // 检查所有注册的引擎的超时实例
                    engines.values.forEach { engine ->
                        checkAndHandleTimeouts(engine)
                    }
                    delay(60000) // 每分钟检查一次
                } catch (e: Exception) {
                    log.error(e) { "超时检查异常" }
                    delay(10000) // 出错后等待10秒再试
                }
            }
        }
    }
    
    /**
     * 异步执行工作流（立即返回，后台执行）
     */
    fun executeAsync(
        engine: SREDEngine,
        instanceId: String,
        autoProcess: Boolean = true,
        onStateChange: ((from: String?, to: String) -> Unit)? = null,
        onComplete: ((state: String) -> Unit)? = null,
        stopStates: Set<String>? = null
    ): Job {
        val job = executionScope.launch {
            try {
                if (autoProcess) {
                    engine.runUntilComplete(
                        instanceId = instanceId,
                        eventType = "process",
                        eventName = "自动处理",
                        onStateChange = onStateChange,
                        onComplete = { state, _ ->
                            onComplete?.invoke(state)
                            runningInstances.remove(instanceId)
                        },
                        onError = { error ->
                            log.error(error) { "工作流执行错误: $instanceId" }
                            runningInstances.remove(instanceId)
                        }
                    )
                } else {
                    // 执行到配置的暂停状态或指定停止状态（支持从持久化恢复）
                    val stopStatesSet = stopStates ?: emptySet()
                    var currentState = engine.getCurrentState(instanceId)
                    val stateMachine = engine.getStateMachine()
                    
                    while (currentState != null) {
                        // 检查状态配置：是否应该暂停
                        val stateDef = stateMachine.getStateDefinition(currentState)
                        val shouldPause = stateDef?.pauseOnEnter == true || 
                                        stopStatesSet.any { currentState!!.contains(it) }
                        
                        if (shouldPause) {
                            log.info { "工作流已暂停在状态: $currentState（基于配置），等待外部触发" }
                            onStateChange?.invoke(null, currentState)
                            
                            // 保存暂停信息到持久化存储（metadata）
                            val pausedAt = System.currentTimeMillis()
                            val timeout = stateDef?.timeout
                            val context = engine.getContext(instanceId)
                            if (context != null) {
                                // 更新上下文的 metadata
                                val updatedMetadata = context.metadata + mapOf(
                                    "_pausedAt" to pausedAt,
                                    "_pausedState" to currentState,
                                    "_pauseTimeout" to (timeout ?: -1)
                                )
                                val updatedContext = context.copy(metadata = updatedMetadata)
                                // 保存到持久化存储
                                engine.getPersistence()?.saveContext(updatedContext)
                            }
                            
                            // 记录暂停信息到内存（用于快速查询）
                            // 确定引擎ID
                            val engineId = findEngineIdByEngine(engine) ?: findEngineId(instanceId) ?: "default"
                            pausedInstances[instanceId] = PauseInfo(
                                instanceId = instanceId,
                                stateId = currentState,
                                pausedAt = pausedAt,
                                timeout = timeout,
                                engineId = engineId
                            )
                            
                            // 移除运行标记，允许长时间停顿
                            runningInstances.remove(instanceId)
                            break
                        }
                        
                        // 检查是否到达终态
                        val finalStates = setOf("success", "completed", "failed", "error")
                        if (finalStates.any { currentState!!.contains(it) }) {
                            log.info { "工作流已完成: $currentState" }
                            onStateChange?.invoke(null, currentState)
                            onComplete?.invoke(currentState)
                            runningInstances.remove(instanceId)
                            pausedInstances.remove(instanceId)
                            break
                        }
                        
                        // 处理事件继续执行（会自动从持久化恢复实例）
                        engine.process(instanceId, "process", "处理")
                        delay(100)
                        currentState = engine.getCurrentState(instanceId)
                    }
                }
            } catch (e: Exception) {
                log.error(e) { "工作流执行异常: $instanceId" }
                runningInstances.remove(instanceId)
            }
        }
        
        runningInstances[instanceId] = job
        return job
    }
    
    /**
     * 继续执行已暂停的工作流
     */
    fun continueExecution(
        engine: SREDEngine,
        instanceId: String,
        eventType: String = "process",
        eventName: String = "继续处理",
        payload: Map<String, Any> = emptyMap(),
        onStateChange: ((from: String?, to: String) -> Unit)? = null,
        onComplete: ((state: String) -> Unit)? = null
    ): Job {
        // 如果有正在运行的任务，先取消
        runningInstances[instanceId]?.cancel()
        
        return executeAsync(
            engine = engine,
            instanceId = instanceId,
            autoProcess = true,
            onStateChange = onStateChange,
            onComplete = onComplete
        )
    }
    
    /**
     * 触发单个事件处理
     */
    suspend fun triggerEvent(
        engine: SREDEngine,
        instanceId: String,
        eventType: String,
        eventName: String,
        payload: Map<String, Any> = emptyMap()
    ): me.ixor.sred.declarative.StateResult {
        return engine.process(instanceId, eventType, eventName, payload)
    }
    
    /**
     * 检查实例是否正在运行
     */
    fun isRunning(instanceId: String): Boolean {
        return runningInstances[instanceId]?.isActive == true
    }
    
    /**
     * 停止实例执行
     */
    fun stop(instanceId: String) {
        runningInstances[instanceId]?.cancel()
        runningInstances.remove(instanceId)
    }
    
    /**
     * 停止所有执行
     */
    fun stopAll() {
        runningInstances.values.forEach { it.cancel() }
        runningInstances.clear()
    }
    
    /**
     * 检查超时的实例（已废弃，使用 checkAndHandleTimeouts）
     */
    @Deprecated("Use checkAndHandleTimeouts instead", ReplaceWith("checkAndHandleTimeouts(engine)"))
    private suspend fun checkTimeouts() {
        // 已移至 checkAndHandleTimeouts
    }
    
    /**
     * 处理超时的实例
     * 
     * @param engine SRED 引擎实例
     * @param instanceId 实例ID
     */
    suspend fun handleTimeout(engine: SREDEngine, instanceId: String) {
        val pauseInfo = pausedInstances[instanceId] ?: return
        val stateMachine = engine.getStateMachine()
        val stateDef = stateMachine.getStateDefinition(pauseInfo.stateId)
        val timeoutAction = stateDef?.timeoutAction
        
        log.info { "处理超时实例: $instanceId, 状态: ${pauseInfo.stateId}" }
        
        if (timeoutAction != null) {
            when (timeoutAction.type) {
                "transition" -> {
                    // 直接转移到目标状态
                    timeoutAction.targetState?.let { targetState ->
                        try {
                            log.info { "超时直接转移到状态: $targetState" }
                            // 使用强制转移，不触发状态函数
                            // forceTransition 内部会验证目标状态存在
                            engine.forceTransition(instanceId, targetState, "timeout")
                        } catch (e: IllegalStateException) {
                            log.error(e) { "超时转移失败: 目标状态 '$targetState' 不存在" }
                            // 如果目标状态不存在，记录错误但不抛出异常，避免影响其他超时实例的处理
                        }
                    }
                }
                "event" -> {
                    // 触发超时事件
                    val eventType = timeoutAction.eventType ?: "timeout"
                    val eventName = timeoutAction.eventName ?: "超时"
                    log.info { "触发超时事件: $eventType" }
                    engine.process(instanceId, eventType, eventName, mapOf("timeout" to true))
                }
            }
            
            // 移除暂停标记（从内存和持久化存储）
            pausedInstances.remove(instanceId)
            
            // 清除持久化存储中的暂停标记
            val context = engine.getContext(instanceId)
            if (context != null && context.metadata.containsKey("_pausedAt")) {
                val updatedMetadata = context.metadata - "_pausedAt" - "_pausedState" - "_pauseTimeout"
                val updatedContext = context.copy(metadata = updatedMetadata)
                engine.getPersistence()?.saveContext(updatedContext)
            }
        } else {
            log.warn { "实例 $instanceId 超时但未配置超时处理操作" }
            // 即使没有配置超时操作，也移除暂停标记（避免重复检查）
            pausedInstances.remove(instanceId)
            
            // 清除持久化存储中的暂停标记
            val context = engine.getContext(instanceId)
            if (context != null && context.metadata.containsKey("_pausedAt")) {
                val updatedMetadata = context.metadata - "_pausedAt" - "_pausedState" - "_pauseTimeout"
                val updatedContext = context.copy(metadata = updatedMetadata)
                engine.getPersistence()?.saveContext(updatedContext)
            }
        }
    }
    
    /**
     * 检查并处理所有超时的实例
     */
    suspend fun checkAndHandleTimeouts(engine: SREDEngine) {
        val now = System.currentTimeMillis()
        val timedOutInstances = mutableListOf<Pair<String, SREDEngine>>()
        
        pausedInstances.values.forEach { pauseInfo ->
            pauseInfo.timeout?.let { timeoutSeconds ->
                if (timeoutSeconds > 0) { // 排除 -1（无限久）和 null
                    val elapsed = (now - pauseInfo.pausedAt) / 1000
                    if (elapsed >= timeoutSeconds) {
                        // 查找对应的引擎
                        val targetEngine = engines[pauseInfo.engineId] ?: engine
                        timedOutInstances.add(pauseInfo.instanceId to targetEngine)
                    }
                }
            }
        }
        
        // 处理超时的实例
        timedOutInstances.forEach { (instanceId, targetEngine) ->
            handleTimeout(targetEngine, instanceId)
        }
    }
    
    /**
     * 获取所有暂停的实例信息
     */
    fun getPausedInstances(): List<PausedInstanceInfo> {
        val now = System.currentTimeMillis()
        return pausedInstances.values.map { pauseInfo ->
            PausedInstanceInfo(
                instanceId = pauseInfo.instanceId,
                stateId = pauseInfo.stateId,
                pausedAt = pauseInfo.pausedAt,
                timeout = pauseInfo.timeout,
                elapsed = (now - pauseInfo.pausedAt) / 1000
            )
        }
    }
    
    /**
     * 移除暂停实例（当状态转移时调用）
     */
    fun removePausedInstance(instanceId: String) {
        pausedInstances.remove(instanceId)
    }
    
    /**
     * 从持久化存储恢复暂停信息
     * 
     * @param engine 引擎实例
     * @param instanceIds 要恢复的实例ID列表（如果为空，则自动从持久化存储查询所有暂停的实例）
     */
    suspend fun restorePausedInstances(engine: SREDEngine, instanceIds: List<String> = emptyList()) {
        val persistence = engine.getPersistence()
        val instanceIdsToRestore = if (instanceIds.isEmpty() && persistence != null) {
            // 从持久化存储查询所有有 _pausedAt 标记的实例
            try {
                persistence.findPausedInstances()
            } catch (e: Exception) {
                log.warn(e) { "查询暂停实例失败，使用空列表" }
                emptyList()
            }
        } else {
            instanceIds
        }
        
        val engineId = findEngineIdByEngine(engine) ?: "default"
        
        instanceIdsToRestore.forEach { instanceId ->
            val context = engine.getContext(instanceId) ?: return@forEach
            val pausedAtObj = context.metadata["_pausedAt"]
            val pausedAt = when (pausedAtObj) {
                is Long -> pausedAtObj
                is Number -> pausedAtObj.toLong()
                else -> return@forEach
            }
            
            val pausedState = context.metadata["_pausedState"] as? String ?: return@forEach
            val timeoutObj = context.metadata["_pauseTimeout"]
            val timeout = when (timeoutObj) {
                is Long -> timeoutObj.takeIf { it >= 0 }
                is Number -> timeoutObj.toLong().takeIf { it >= 0 }
                else -> null
            }
            
            pausedInstances[instanceId] = PauseInfo(
                instanceId = instanceId,
                stateId = pausedState,
                pausedAt = pausedAt,
                timeout = timeout,
                engineId = engineId
            )
            
            val elapsed = (System.currentTimeMillis() - pausedAt) / 1000
            log.info { "恢复暂停实例: $instanceId, 状态: $pausedState, 已暂停: ${elapsed}秒" }
            
            // 如果已经超时，立即处理
            timeout?.let { timeoutSeconds ->
                if (timeoutSeconds > 0 && elapsed >= timeoutSeconds) {
                    log.warn { "恢复的暂停实例已超时，立即处理: $instanceId" }
                    handleTimeout(engine, instanceId)
                }
            }
        }
    }
    
    /**
     * 根据引擎实例查找引擎ID
     */
    private fun findEngineIdByEngine(engine: SREDEngine): String? {
        return engines.entries.find { it.value == engine }?.key
    }
    
    /**
     * 关闭执行器
     */
    fun close() {
        timeoutMonitorJob?.cancel()
        stopAll()
        executionScope.cancel()
    }
}
