package me.ixor.sred.discovery

import me.ixor.sred.core.*
import me.ixor.sred.health.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 状态服务接口
 */
interface StateService {
    /**
     * 服务ID
     */
    val serviceId: String
    
    /**
     * 服务名称
     */
    val serviceName: String
    
    /**
     * 服务地址
     */
    val serviceAddress: URI?
    
    /**
     * 服务负责的状态ID列表
     */
    val stateIds: List<StateId>
    
    /**
     * 服务版本
     */
    val version: String
    
    /**
     * 兼容的版本列表（支持语义化版本，如 "1.x", "2.0"）
     */
    val compatibleVersions: List<String>
    
    /**
     * 服务元数据
     */
    val metadata: Map<String, Any>
    
    /**
     * 健康检查
     */
    val healthCheck: HealthCheck?
}

/**
 * 服务事件
 */
sealed class ServiceEvent {
    abstract val serviceId: String
    abstract val timestamp: Instant
    
    /**
     * 服务注册
     */
    data class Registered(
        override val serviceId: String,
        val service: StateService,
        override val timestamp: Instant = Instant.now()
    ) : ServiceEvent()
    
    /**
     * 服务注销
     */
    data class Unregistered(
        override val serviceId: String,
        override val timestamp: Instant = Instant.now()
    ) : ServiceEvent()
    
    /**
     * 服务健康状态变化
     */
    data class HealthStatusChanged(
        override val serviceId: String,
        val oldStatus: HealthStatusType,
        val newStatus: HealthStatusType,
        override val timestamp: Instant = Instant.now()
    ) : ServiceEvent()
    
    /**
     * 服务版本更新
     */
    data class VersionUpdated(
        override val serviceId: String,
        val oldVersion: String,
        val newVersion: String,
        override val timestamp: Instant = Instant.now()
    ) : ServiceEvent()
}

/**
 * 服务注册表接口
 */
interface ServiceRegistry {
    /**
     * 注册服务
     */
    suspend fun registerService(
        service: StateService,
        healthCheck: HealthCheck? = null
    )
    
    /**
     * 注销服务
     */
    suspend fun unregisterService(serviceId: String)
    
    /**
     * 发现服务（根据状态ID）
     */
    suspend fun discoverServicesForState(stateId: StateId): List<StateService>
    
    /**
     * 发现服务（根据服务ID）
     */
    suspend fun discoverService(serviceId: String): StateService?
    
    /**
     * 获取所有服务
     */
    suspend fun getAllServices(): List<StateService>
    
    /**
     * 监听服务变化
     */
    fun watchServices(): Flow<ServiceEvent>
    
    /**
     * 监听特定状态的服务变化
     */
    fun watchServicesForState(stateId: StateId): Flow<ServiceEvent>
    
    /**
     * 更新服务健康状态
     */
    suspend fun updateServiceHealth(serviceId: String, status: HealthStatus)
    
    /**
     * 获取服务的健康状态
     */
    suspend fun getServiceHealth(serviceId: String): HealthStatus?
    
    /**
     * 检查服务是否健康
     */
    suspend fun isServiceHealthy(serviceId: String): Boolean
    
    /**
     * 检查服务版本兼容性
     * @param serviceId1 服务1的ID
     * @param serviceId2 服务2的ID
     * @return 是否兼容
     */
    suspend fun areServicesCompatible(serviceId1: String, serviceId2: String): Boolean
    
    /**
     * 执行平滑升级（Rolling Update）
     * @param oldServiceId 旧服务ID
     * @param newServiceId 新服务ID
     * @param updateStrategy 升级策略
     * @return 升级ID
     */
    suspend fun performRollingUpdate(
        oldServiceId: String,
        newServiceId: String,
        updateStrategy: RollingUpdateStrategy = RollingUpdateStrategy.GRACEFUL
    ): String
    
    /**
     * 获取服务的兼容服务列表
     * @param serviceId 服务ID
     * @return 兼容的服务列表
     */
    suspend fun getCompatibleServices(serviceId: String): List<StateService>
}

/**
 * 平滑升级策略
 */
enum class RollingUpdateStrategy {
    /**
     * 优雅升级：等待旧服务完成任务后再切换
     */
    GRACEFUL,
    
    /**
     * 立即升级：立即切换到新服务
     */
    IMMEDIATE,
    
    /**
     * 蓝绿部署：并行运行，逐步切换流量
     */
    BLUE_GREEN,
    
    /**
     * 金丝雀发布：逐步增加新服务的流量比例
     */
    CANARY
}

/**
 * 版本兼容性管理器
 */
object VersionCompatibilityManager {
    /**
     * 检查版本是否兼容
     * @param version1 版本1
     * @param version2 版本2
     * @param compatibleVersions 兼容版本列表
     * @return 是否兼容
     */
    fun isVersionCompatible(
        version1: String,
        version2: String,
        compatibleVersions: List<String>
    ): Boolean {
        // 完全匹配
        if (version1 == version2) return true
        
        // 检查是否在兼容列表中
        return compatibleVersions.any { compatible ->
            matchesVersionPattern(version1, compatible) || 
            matchesVersionPattern(version2, compatible)
        }
    }
    
    /**
     * 匹配版本模式
     * 支持：
     * - "1.x" 匹配 "1.0", "1.1", "1.2" 等
     * - "1.0" 精确匹配
     * - ">=1.0" 版本范围
     */
    private fun matchesVersionPattern(version: String, pattern: String): Boolean {
        return when {
            pattern == version -> true
            pattern.endsWith(".x") -> {
                val patternPrefix = pattern.removeSuffix(".x")
                version.startsWith("$patternPrefix.")
            }
            pattern.startsWith(">=") -> {
                val minVersion = pattern.removePrefix(">=")
                compareVersions(version, minVersion) >= 0
            }
            pattern.startsWith("<=") -> {
                val maxVersion = pattern.removePrefix("<=")
                compareVersions(version, maxVersion) <= 0
            }
            else -> false
        }
    }
    
    /**
     * 比较版本号
     * @return 负数表示v1 < v2，0表示相等，正数表示v1 > v2
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val part1 = parts1.getOrNull(i) ?: 0
            val part2 = parts2.getOrNull(i) ?: 0
            when {
                part1 < part2 -> return -1
                part1 > part2 -> return 1
            }
        }
        return 0
    }
}

/**
 * 服务注册表实现
 */
class ServiceRegistryImpl(
    private val healthCheckManager: HealthCheckManager = HealthCheckFactory.createManager()
) : ServiceRegistry {
    
    private val services = ConcurrentHashMap<String, StateService>()
    private val stateToServices = ConcurrentHashMap<StateId, MutableSet<String>>()
    private val serviceHealthStatuses = ConcurrentHashMap<String, HealthStatus>()
    private val serviceEventsFlow = MutableSharedFlow<ServiceEvent>(
        extraBufferCapacity = 64
    )
    
    private val mutex = kotlinx.coroutines.sync.Mutex()
    
    init {
        // 启动健康检查管理器
        CoroutineScope(Dispatchers.Default).launch {
            healthCheckManager.startPeriodicChecks(this)
        }
    }
    
    override suspend fun registerService(
        service: StateService,
        healthCheck: HealthCheck?
    ) {
        mutex.withLock {
            val oldService = services[service.serviceId]
            
            services[service.serviceId] = service
            
            // 更新状态到服务的映射
            service.stateIds.forEach { stateId ->
                stateToServices.getOrPut(stateId) { mutableSetOf() }.add(service.serviceId)
            }
            
            // 注册健康检查
            val actualHealthCheck = healthCheck ?: service.healthCheck
            if (actualHealthCheck != null) {
                healthCheckManager.registerHealthCheck(actualHealthCheck)
                
                // 执行初始健康检查
                val healthResult = healthCheckManager.performCheck(actualHealthCheck.id)
                if (healthResult != null) {
                    serviceHealthStatuses[service.serviceId] = healthResult.status
                }
                
                // 监听健康状态变化
                CoroutineScope(Dispatchers.Default).launch {
                    healthCheckManager.watchHealthStatus(actualHealthCheck.id).collect { status ->
                        val oldStatus = serviceHealthStatuses[service.serviceId]?.status
                        serviceHealthStatuses[service.serviceId] = status
                        
                        if (oldStatus != status.status) {
                            serviceEventsFlow.emit(
                                ServiceEvent.HealthStatusChanged(
                                    serviceId = service.serviceId,
                                    oldStatus = oldStatus ?: HealthStatusType.UNKNOWN,
                                    newStatus = status.status
                                )
                            )
                        }
                    }
                }
            }
            
            // 发送注册事件
            val event = if (oldService == null) {
                ServiceEvent.Registered(serviceId = service.serviceId, service = service)
            } else if (oldService.version != service.version) {
                ServiceEvent.VersionUpdated(
                    serviceId = service.serviceId,
                    oldVersion = oldService.version,
                    newVersion = service.version
                )
            } else {
                null
            }
            // 在协程中发送事件
            if (event != null) {
                CoroutineScope(Dispatchers.Default).launch {
                    serviceEventsFlow.emit(event)
                }
            }
        }
    }
    
    override suspend fun unregisterService(serviceId: String) {
        mutex.withLock {
            val service = services.remove(serviceId) ?: return
            
            // 更新状态映射
            service.stateIds.forEach { stateId ->
                stateToServices[stateId]?.remove(serviceId)
                if (stateToServices[stateId]?.isEmpty() == true) {
                    stateToServices.remove(stateId)
                }
            }
            
            // 注销健康检查
            service.healthCheck?.let {
                healthCheckManager.unregisterHealthCheck(it.id)
            }
            
            serviceHealthStatuses.remove(serviceId)
            
            // 发送注销事件
            CoroutineScope(Dispatchers.Default).launch {
                serviceEventsFlow.emit(
                    ServiceEvent.Unregistered(serviceId = serviceId)
                )
            }
        }
    }
    
    override suspend fun discoverServicesForState(stateId: StateId): List<StateService> {
        return mutex.withLock {
            val serviceIds = stateToServices[stateId] ?: return@withLock emptyList()
            val healthyServices = mutableListOf<StateService>()
            for (serviceId in serviceIds) {
                val service = services[serviceId] ?: continue
                val health = serviceHealthStatuses[serviceId]
                if (health?.status == HealthStatusType.HEALTHY) {
                    healthyServices.add(service)
                }
            }
            healthyServices
        }
    }
    
    override suspend fun discoverService(serviceId: String): StateService? {
        return mutex.withLock {
            services[serviceId]
        }
    }
    
    override suspend fun getAllServices(): List<StateService> {
        return mutex.withLock {
            services.values.toList()
        }
    }
    
    override fun watchServices(): Flow<ServiceEvent> {
        return serviceEventsFlow
    }
    
    override fun watchServicesForState(stateId: StateId): Flow<ServiceEvent> {
        return watchServices()
            .filter { event ->
                when (event) {
                    is ServiceEvent.Registered -> event.service.stateIds.contains(stateId)
                    else -> {
                        // 直接访问线程安全的ConcurrentHashMap
                        val service = services[event.serviceId]
                        service?.stateIds?.contains(stateId) == true
                    }
                }
            }
    }
    
    override suspend fun updateServiceHealth(serviceId: String, status: HealthStatus) {
        mutex.withLock {
            val oldStatus = serviceHealthStatuses[serviceId]?.status
            serviceHealthStatuses[serviceId] = status
            
            if (oldStatus != status.status) {
                CoroutineScope(Dispatchers.Default).launch {
                    serviceEventsFlow.emit(
                        ServiceEvent.HealthStatusChanged(
                            serviceId = serviceId,
                            oldStatus = oldStatus ?: HealthStatusType.UNKNOWN,
                            newStatus = status.status
                        )
                    )
                }
            }
        }
    }
    
    override suspend fun getServiceHealth(serviceId: String): HealthStatus? {
        return mutex.withLock {
            serviceHealthStatuses[serviceId]
        }
    }
    
    override suspend fun isServiceHealthy(serviceId: String): Boolean {
        val health = getServiceHealth(serviceId)
        return health?.status == HealthStatusType.HEALTHY
    }
    
    private val rollingUpdates = ConcurrentHashMap<String, RollingUpdateInfo>()
    
    override suspend fun areServicesCompatible(serviceId1: String, serviceId2: String): Boolean {
        return mutex.withLock {
            val service1 = services[serviceId1] ?: return@withLock false
            val service2 = services[serviceId2] ?: return@withLock false
            
            VersionCompatibilityManager.isVersionCompatible(
                version1 = service1.version,
                version2 = service2.version,
                compatibleVersions = service1.compatibleVersions + service2.compatibleVersions
            )
        }
    }
    
    override suspend fun performRollingUpdate(
        oldServiceId: String,
        newServiceId: String,
        updateStrategy: RollingUpdateStrategy
    ): String {
        return mutex.withLock {
            val oldService = services[oldServiceId] 
                ?: throw IllegalArgumentException("Old service not found: $oldServiceId")
            val newService = services[newServiceId]
                ?: throw IllegalArgumentException("New service not found: $newServiceId")
            
            // 检查兼容性
            if (!areServicesCompatible(oldServiceId, newServiceId)) {
                throw IllegalArgumentException(
                    "Services are not compatible: $oldServiceId (${oldService.version}) vs $newServiceId (${newService.version})"
                )
            }
            
            val updateId = "update_${oldServiceId}_${System.currentTimeMillis()}"
            
            rollingUpdates[updateId] = RollingUpdateInfo(
                updateId = updateId,
                oldServiceId = oldServiceId,
                newServiceId = newServiceId,
                strategy = updateStrategy,
                status = RollingUpdateStatus.IN_PROGRESS,
                startedAt = Instant.now()
            )
            
            // 根据策略执行升级
            when (updateStrategy) {
                RollingUpdateStrategy.GRACEFUL -> {
                    // 等待旧服务完成任务（这里简化实现）
                    // 实际应该等待正在处理的任务完成
                    performGracefulUpdate(oldServiceId, newServiceId, updateId)
                }
                RollingUpdateStrategy.IMMEDIATE -> {
                    performImmediateUpdate(oldServiceId, newServiceId, updateId)
                }
                RollingUpdateStrategy.BLUE_GREEN -> {
                    performBlueGreenUpdate(oldServiceId, newServiceId, updateId)
                }
                RollingUpdateStrategy.CANARY -> {
                    performCanaryUpdate(oldServiceId, newServiceId, updateId)
                }
            }
            
            updateId
        }
    }
    
    override suspend fun getCompatibleServices(serviceId: String): List<StateService> {
        return mutex.withLock {
            val service = services[serviceId] ?: return@withLock emptyList()
            
            services.values.filter { otherService ->
                if (otherService.serviceId == serviceId) return@filter false
                VersionCompatibilityManager.isVersionCompatible(
                    version1 = service.version,
                    version2 = otherService.version,
                    compatibleVersions = service.compatibleVersions + otherService.compatibleVersions
                )
            }
        }
    }
    
    private suspend fun performGracefulUpdate(
        oldServiceId: String,
        newServiceId: String,
        updateId: String
    ) {
        // 1. 停止接受新请求到旧服务
        // 2. 等待正在处理的任务完成
        // 3. 切换到新服务
        // 4. 注销旧服务
        
        // 简化实现：直接切换
        val oldService = services[oldServiceId]!!
        val oldStateIds = oldService.stateIds
        
        // 注销旧服务
        unregisterService(oldServiceId)
        
        // 更新升级状态
        rollingUpdates[updateId] = rollingUpdates[updateId]!!.copy(
            status = RollingUpdateStatus.COMPLETED,
            completedAt = Instant.now()
        )
    }
    
    private suspend fun performImmediateUpdate(
        oldServiceId: String,
        newServiceId: String,
        updateId: String
    ) {
        // 立即切换到新服务
        unregisterService(oldServiceId)
        
        rollingUpdates[updateId] = rollingUpdates[updateId]!!.copy(
            status = RollingUpdateStatus.COMPLETED,
            completedAt = Instant.now()
        )
    }
    
    private suspend fun performBlueGreenUpdate(
        oldServiceId: String,
        newServiceId: String,
        updateId: String
    ) {
        // 蓝绿部署：并行运行，逐步切换流量
        // 新服务已经注册，这里只需要逐步切换流量
        
        rollingUpdates[updateId] = rollingUpdates[updateId]!!.copy(
            status = RollingUpdateStatus.COMPLETED,
            completedAt = Instant.now()
        )
    }
    
    private suspend fun performCanaryUpdate(
        oldServiceId: String,
        newServiceId: String,
        updateId: String
    ) {
        // 金丝雀发布：逐步增加新服务流量
        // 实际应该通过负载均衡器逐步增加流量比例
        
        rollingUpdates[updateId] = rollingUpdates[updateId]!!.copy(
            status = RollingUpdateStatus.COMPLETED,
            completedAt = Instant.now()
        )
    }
}

/**
 * 平滑升级信息
 */
data class RollingUpdateInfo(
    val updateId: String,
    val oldServiceId: String,
    val newServiceId: String,
    val strategy: RollingUpdateStrategy,
    val status: RollingUpdateStatus,
    val startedAt: Instant,
    val completedAt: Instant? = null,
    val trafficSplit: Double = 0.0  // 新服务的流量比例（0.0-1.0）
)

/**
 * 平滑升级状态
 */
enum class RollingUpdateStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * 负载均衡器
 */
interface LoadBalancer {
    /**
     * 从服务列表中选择一个服务
     */
    suspend fun selectService(services: List<StateService>): StateService?
}

/**
 * 轮询负载均衡器
 */
class RoundRobinLoadBalancer : LoadBalancer {
    private var currentIndex = 0
    private val mutex = Mutex()
    
    override suspend fun selectService(services: List<StateService>): StateService? {
        if (services.isEmpty()) return null
        
        return mutex.withLock {
            val service = services[currentIndex % services.size]
            currentIndex++
            service
        }
    }
}

/**
 * 随机负载均衡器
 */
class RandomLoadBalancer : LoadBalancer {
    override suspend fun selectService(services: List<StateService>): StateService? {
        if (services.isEmpty()) return null
        return services.random()
    }
}

/**
 * 基于健康状态的负载均衡器（优先选择更健康的服务）
 */
class HealthAwareLoadBalancer(
    private val serviceRegistry: ServiceRegistry
) : LoadBalancer {
    
    override suspend fun selectService(services: List<StateService>): StateService? {
        if (services.isEmpty()) return null
        
        // 按健康状态排序，优先选择健康的服务
        val servicesWithHealth = services.map { service ->
            val health = serviceRegistry.getServiceHealth(service.serviceId)
            val healthScore = when (health?.status) {
                HealthStatusType.HEALTHY -> 3
                HealthStatusType.UNHEALTHY -> 2
                HealthStatusType.UNKNOWN -> 1
                HealthStatusType.STOPPED -> 0
                null -> 1
            }
            Pair(service, healthScore)
        }
        
        val sortedServices = servicesWithHealth.sortedByDescending { it.second }
        return sortedServices.firstOrNull()?.first
    }
}

/**
 * 服务发现工厂
 */
object ServiceDiscoveryFactory {
    fun createRegistry(
        healthCheckManager: HealthCheckManager? = null
    ): ServiceRegistry {
        return ServiceRegistryImpl(healthCheckManager ?: HealthCheckFactory.createManager())
    }
    
    fun createRoundRobinBalancer(): LoadBalancer = RoundRobinLoadBalancer()
    fun createRandomBalancer(): LoadBalancer = RandomLoadBalancer()
    fun createHealthAwareBalancer(serviceRegistry: ServiceRegistry): LoadBalancer =
        HealthAwareLoadBalancer(serviceRegistry)
}

