package me.ixor.sred.policy

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 策略版本管理器
 */
class PolicyVersionManager {
    private val mutex = Mutex()
    private val policyVersions = ConcurrentHashMap<String, MutableList<PolicyVersion>>()
    private val abTests = ConcurrentHashMap<String, ABTestConfig>()
    private val gradualRollouts = ConcurrentHashMap<String, GradualRolloutConfig>()
    
    /**
     * 记录策略版本
     */
    suspend fun recordVersion(policyId: String, policy: TransitionPolicy) {
        mutex.withLock {
            val versions = policyVersions.getOrPut(policyId) { mutableListOf() }
            versions.add(
                PolicyVersion(
                    policy = policy,
                    timestamp = Instant.now()
                )
            )
            // 保持最近100个版本
            if (versions.size > 100) {
                versions.removeAt(0)
            }
        }
    }
    
    /**
     * 获取策略版本历史
     */
    suspend fun getPolicyHistory(policyId: String): List<TransitionPolicy> {
        return mutex.withLock {
            policyVersions[policyId]?.map { it.policy }?.sortedByDescending { 
                it.version 
            } ?: emptyList()
        }
    }
    
    /**
     * 查找指定版本的策略
     */
    suspend fun findVersion(policyId: String, version: String): TransitionPolicy? {
        return mutex.withLock {
            policyVersions[policyId]?.find { it.policy.version == version }?.policy
        }
    }
    
    /**
     * 启用A/B测试
     */
    suspend fun enableABTest(
        policyIdA: String,
        policyIdB: String,
        trafficSplit: Double,
        testId: String
    ) {
        mutex.withLock {
            abTests[testId] = ABTestConfig(
                testId = testId,
                policyIdA = policyIdA,
                policyIdB = policyIdB,
                trafficSplit = trafficSplit.coerceIn(0.0, 1.0),
                enabled = true,
                createdAt = Instant.now()
            )
        }
    }
    
    /**
     * 停止A/B测试
     */
    suspend fun stopABTest(testId: String) {
        mutex.withLock {
            abTests[testId]?.let {
                abTests[testId] = it.copy(enabled = false)
            }
        }
    }
    
    /**
     * 获取A/B测试配置
     */
    suspend fun getABTest(testId: String): ABTestConfig? {
        return mutex.withLock {
            abTests[testId]
        }
    }
    
    /**
     * 选择A/B测试策略
     */
    suspend fun selectABTestPolicy(
        testId: String,
        contextHash: Int? = null
    ): String? {
        val test = abTests[testId] ?: return null
        if (!test.enabled) return null
        
        // 使用上下文哈希或随机数决定流量分割
        val randomValue = if (contextHash != null) {
            (contextHash % 100).toDouble() / 100.0
        } else {
            Random.nextDouble()
        }
        
        return if (randomValue < test.trafficSplit) {
            test.policyIdB
        } else {
            test.policyIdA
        }
    }
    
    /**
     * 启用灰度发布
     */
    suspend fun enableGradualRollout(
        policyId: String,
        rolloutPercentage: Int
    ): String {
        val rolloutId = "${policyId}_rollout_${System.currentTimeMillis()}"
        mutex.withLock {
            gradualRollouts[rolloutId] = GradualRolloutConfig(
                rolloutId = rolloutId,
                policyId = policyId,
                rolloutPercentage = rolloutPercentage.coerceIn(0, 100),
                enabled = true,
                createdAt = Instant.now()
            )
        }
        return rolloutId
    }
    
    /**
     * 更新灰度发布百分比
     */
    suspend fun updateGradualRollout(rolloutId: String, rolloutPercentage: Int) {
        mutex.withLock {
            gradualRollouts[rolloutId]?.let {
                gradualRollouts[rolloutId] = it.copy(
                    rolloutPercentage = rolloutPercentage.coerceIn(0, 100)
                )
            }
        }
    }
    
    /**
     * 完成灰度发布
     */
    suspend fun completeGradualRollout(rolloutId: String) {
        mutex.withLock {
            gradualRollouts[rolloutId]?.let {
                gradualRollouts[rolloutId] = it.copy(
                    rolloutPercentage = 100,
                    enabled = false
                )
            }
        }
    }
    
    /**
     * 获取灰度发布配置
     */
    suspend fun getGradualRollout(rolloutId: String): GradualRolloutConfig? {
        return mutex.withLock {
            gradualRollouts[rolloutId]
        }
    }
    
    /**
     * 检查是否应该应用灰度发布策略
     */
    suspend fun shouldApplyGradualRollout(
        policyId: String,
        contextHash: Int? = null
    ): Boolean {
        val rollout = gradualRollouts.values.find { 
            it.policyId == policyId && it.enabled 
        } ?: return true  // 没有灰度发布配置，默认应用
        
        val randomValue = if (contextHash != null) {
            (contextHash % 100)
        } else {
            Random.nextInt(100)
        }
        
        return randomValue < rollout.rolloutPercentage
    }
    
    /**
     * 获取所有A/B测试
     */
    suspend fun getAllABTests(): List<ABTestConfig> {
        return mutex.withLock {
            abTests.values.toList()
        }
    }
    
    /**
     * 获取所有灰度发布
     */
    suspend fun getAllGradualRollouts(): List<GradualRolloutConfig> {
        return mutex.withLock {
            gradualRollouts.values.toList()
        }
    }
}

/**
 * 策略版本
 */
data class PolicyVersion(
    val policy: TransitionPolicy,
    val timestamp: Instant
)

/**
 * A/B测试配置
 */
data class ABTestConfig(
    val testId: String,
    val policyIdA: String,
    val policyIdB: String,
    val trafficSplit: Double,  // 0.0-1.0，表示B的流量比例
    val enabled: Boolean,
    val createdAt: Instant,
    val stoppedAt: Instant? = null
)

/**
 * 灰度发布配置
 */
data class GradualRolloutConfig(
    val rolloutId: String,
    val policyId: String,
    var rolloutPercentage: Int,  // 0-100
    val enabled: Boolean,
    val createdAt: Instant,
    val completedAt: Instant? = null
)



