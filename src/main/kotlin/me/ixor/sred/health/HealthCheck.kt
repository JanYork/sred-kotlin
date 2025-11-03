package me.ixor.sred.health

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.Duration

/**
 * 健康检查接口
 * 
 * 符合论文要求：支持服务健康检查和故障转移
 */
interface HealthCheck {
    /**
     * 健康检查ID
     */
    val id: String
    
    /**
     * 执行健康检查
     * @return 健康状态
     */
    suspend fun check(): HealthStatus
    
    /**
     * 检查是否健康（快速判断）
     * @return 是否健康
     */
    suspend fun isHealthy(): Boolean = check().status == HealthStatusType.HEALTHY
    
    /**
     * 获取健康检查间隔
     * @return 检查间隔（秒）
     */
    fun getCheckInterval(): Duration
}

/**
 * 健康状态类型
 */
enum class HealthStatusType {
    /**
     * 健康：服务正常运行
     */
    HEALTHY,
    
    /**
     * 不健康：服务存在问题但仍在运行
     */
    UNHEALTHY,
    
    /**
     * 已停止：服务已停止
     */
    STOPPED,
    
    /**
     * 未知：无法确定状态
     */
    UNKNOWN
}

/**
 * 健康状态
 */
data class HealthStatus(
    val status: HealthStatusType,
    val message: String = "",
    val timestamp: Instant = Instant.now(),
    val details: Map<String, Any> = emptyMap(),
    val lastCheckTime: Instant? = null,
    val checkDuration: Duration? = null
)

/**
 * 健康检查结果
 */
data class HealthCheckResult(
    val checkId: String,
    val status: HealthStatus,
    val timestamp: Instant = Instant.now(),
    val nextCheckTime: Instant? = null
)

/**
 * 健康检查管理器
 */
interface HealthCheckManager {
    /**
     * 注册健康检查
     */
    suspend fun registerHealthCheck(healthCheck: HealthCheck)
    
    /**
     * 注销健康检查
     */
    suspend fun unregisterHealthCheck(checkId: String)
    
    /**
     * 执行健康检查
     */
    suspend fun performCheck(checkId: String): HealthCheckResult?
    
    /**
     * 执行所有健康检查
     */
    suspend fun performAllChecks(): Map<String, HealthCheckResult>
    
    /**
     * 启动定期健康检查
     */
    suspend fun startPeriodicChecks(scope: CoroutineScope)
    
    /**
     * 停止定期健康检查
     */
    suspend fun stopPeriodicChecks()
    
    /**
     * 获取健康检查结果
     */
    fun getHealthStatus(checkId: String): HealthStatus?
    
    /**
     * 获取所有健康检查结果
     */
    fun getAllHealthStatuses(): Map<String, HealthStatus>
    
    /**
     * 监听健康状态变化
     */
    fun watchHealthStatus(checkId: String): Flow<HealthStatus>
}

/**
 * 简单健康检查实现（Lambda方式）
 */
class SimpleHealthCheck(
    override val id: String,
    private val checkFunction: suspend () -> HealthStatus,
    private val checkInterval: Duration = Duration.ofSeconds(30)
) : HealthCheck {
    
    override suspend fun check(): HealthStatus {
        return checkFunction()
    }
    
    override fun getCheckInterval(): Duration = checkInterval
}

/**
 * 状态健康检查 - 检查状态管理器的健康状态
 */
class StateManagerHealthCheck(
    override val id: String = "state_manager",
    private val stateManager: me.ixor.sred.state.StateManager,
    private val checkInterval: Duration = Duration.ofSeconds(30)
) : HealthCheck {
    
    override suspend fun check(): HealthStatus {
        return try {
            val stats = stateManager.getStatistics()
            
            HealthStatus(
                status = HealthStatusType.HEALTHY,
                message = "State manager is operational",
                details = mapOf(
                    "contextCount" to stats.contextCount,
                    "totalTransitions" to stats.totalTransitions,
                    "averageTransitionTimeMs" to stats.averageTransitionTimeMs
                )
            )
        } catch (e: Exception) {
            HealthStatus(
                status = HealthStatusType.UNHEALTHY,
                message = "Health check failed: ${e.message}",
                details = mapOf("error" to e.javaClass.simpleName)
            )
        }
    }
    
    override fun getCheckInterval(): Duration = checkInterval
}

/**
 * 事件总线健康检查
 */
class EventBusHealthCheck(
    override val id: String = "event_bus",
    private val eventBus: me.ixor.sred.event.EventBus,
    private val checkInterval: Duration = Duration.ofSeconds(30)
) : HealthCheck {
    
    override suspend fun check(): HealthStatus {
        return try {
            val stats = eventBus.getStatistics()
            
            HealthStatus(
                status = HealthStatusType.HEALTHY,
                message = "Event bus is operational",
                details = mapOf(
                    "totalEventsPublished" to stats.totalEventsPublished,
                    "totalEventsProcessed" to stats.totalEventsProcessed,
                    "activeSubscriptions" to stats.activeSubscriptions,
                    "averageProcessingTimeMs" to stats.averageProcessingTimeMs
                )
            )
        } catch (e: Exception) {
            HealthStatus(
                status = HealthStatusType.UNHEALTHY,
                message = "Health check failed: ${e.message}",
                details = mapOf("error" to e.javaClass.simpleName)
            )
        }
    }
    
    override fun getCheckInterval(): Duration = checkInterval
}

/**
 * 持久化适配器健康检查
 */
class PersistenceHealthCheck(
    override val id: String = "persistence",
    private val persistence: me.ixor.sred.persistence.ExtendedStatePersistence,
    private val checkInterval: Duration = Duration.ofSeconds(60)
) : HealthCheck {
    
    override suspend fun check(): HealthStatus {
        return try {
            // 尝试列出上下文ID来验证连接
            val contextIds = persistence.listContextIds()
            
            HealthStatus(
                status = HealthStatusType.HEALTHY,
                message = "Persistence adapter is operational",
                details = mapOf(
                    "totalContexts" to contextIds.size
                )
            )
        } catch (e: Exception) {
            HealthStatus(
                status = HealthStatusType.UNHEALTHY,
                message = "Persistence health check failed: ${e.message}",
                details = mapOf("error" to e.javaClass.simpleName)
            )
        }
    }
    
    override fun getCheckInterval(): Duration = checkInterval
}

/**
 * 健康检查管理器实现
 */
class HealthCheckManagerImpl : HealthCheckManager {
    
    private val healthChecks = mutableMapOf<String, HealthCheck>()
    private val healthStatuses = mutableMapOf<String, HealthStatus>()
    private val statusFlows = mutableMapOf<String, MutableSharedFlow<HealthStatus>>()
    private var periodicCheckJob: Job? = null
    
    override suspend fun registerHealthCheck(healthCheck: HealthCheck) {
        healthChecks[healthCheck.id] = healthCheck
        
        // 创建状态流
        statusFlows[healthCheck.id] = MutableSharedFlow(
            extraBufferCapacity = 64
        )
        
        // 执行初始检查
        val result = performCheck(healthCheck.id)
        if (result != null) {
            healthStatuses[healthCheck.id] = result.status
        }
    }
    
    override suspend fun unregisterHealthCheck(checkId: String) {
        healthChecks.remove(checkId)
        healthStatuses.remove(checkId)
        statusFlows.remove(checkId)
    }
    
    override suspend fun performCheck(checkId: String): HealthCheckResult? {
        val healthCheck = healthChecks[checkId] ?: return null
        
        val startTime = Instant.now()
        val status = healthCheck.check()
        val endTime = Instant.now()
        val duration = Duration.between(startTime, endTime)
        
        val result = HealthCheckResult(
            checkId = checkId,
            status = status.copy(
                lastCheckTime = startTime,
                checkDuration = duration
            ),
            nextCheckTime = startTime.plus(healthCheck.getCheckInterval())
        )
        
        // 更新状态
        val oldStatus = healthStatuses[checkId]
        healthStatuses[checkId] = result.status
        
        // 如果状态变化，发送到流
        if (oldStatus?.status != result.status.status) {
            CoroutineScope(Dispatchers.Default).launch {
                statusFlows[checkId]?.emit(result.status)
            }
        }
        
        return result
    }
    
    override suspend fun performAllChecks(): Map<String, HealthCheckResult> {
        return healthChecks.keys.mapNotNull { checkId ->
            performCheck(checkId)?.let { checkId to it }
        }.toMap()
    }
    
    override suspend fun startPeriodicChecks(scope: CoroutineScope) {
        stopPeriodicChecks()
        
        periodicCheckJob = scope.launch {
            while (isActive) {
                // 并行执行所有健康检查
                healthChecks.values.forEach { healthCheck ->
                    launch {
                        try {
                            performCheck(healthCheck.id)
                        } catch (e: Exception) {
                            // 记录错误但不中断其他检查
                            println("Health check failed for ${healthCheck.id}: ${e.message}")
                        }
                    }
                }
                
                // 等待下一个检查周期（使用最小间隔）
                val minInterval = healthChecks.values
                    .map { it.getCheckInterval() }
                    .minOrNull() ?: Duration.ofSeconds(30)
                delay(minInterval.toMillis())
            }
        }
    }
    
    override suspend fun stopPeriodicChecks() {
        periodicCheckJob?.cancel()
        periodicCheckJob = null
    }
    
    override fun getHealthStatus(checkId: String): HealthStatus? {
        return healthStatuses[checkId]
    }
    
    override fun getAllHealthStatuses(): Map<String, HealthStatus> {
        return healthStatuses.toMap()
    }
    
    override fun watchHealthStatus(checkId: String): Flow<HealthStatus> {
        return statusFlows.getOrPut(checkId) {
            MutableSharedFlow(
                extraBufferCapacity = 64
            )
        }
    }
}

/**
 * 健康检查工厂
 */
object HealthCheckFactory {
    fun createManager(): HealthCheckManager = HealthCheckManagerImpl()
    
    fun createStateManagerCheck(
        stateManager: me.ixor.sred.state.StateManager,
        checkInterval: Duration = Duration.ofSeconds(30)
    ): HealthCheck = StateManagerHealthCheck(
        stateManager = stateManager,
        checkInterval = checkInterval
    )
    
    fun createEventBusCheck(
        eventBus: me.ixor.sred.event.EventBus,
        checkInterval: Duration = Duration.ofSeconds(30)
    ): HealthCheck = EventBusHealthCheck(
        eventBus = eventBus,
        checkInterval = checkInterval
    )
    
    fun createPersistenceCheck(
        persistence: me.ixor.sred.persistence.ExtendedStatePersistence,
        checkInterval: Duration = Duration.ofSeconds(60)
    ): HealthCheck = PersistenceHealthCheck(
        persistence = persistence,
        checkInterval = checkInterval
    )
    
    fun createSimpleCheck(
        id: String,
        checkFunction: suspend () -> HealthStatus,
        checkInterval: Duration = Duration.ofSeconds(30)
    ): HealthCheck = SimpleHealthCheck(
        id = id,
        checkFunction = checkFunction,
        checkInterval = checkInterval
    )
}

