package me.ixor.sred.policy

import me.ixor.sred.core.*
import kotlinx.coroutines.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 策略引擎 - 动态策略调整和决策
 * 
 * 符合论文要求："系统策略可在运行时实时变更，无需重构"
 * "系统能根据全局负载、配置版本或时间窗口，在相同事件下选择不同的状态转移路径"
 * 
 * 策略引擎能够：
 * 1. 在运行时动态加载、更新策略
 * 2. 基于策略评估状态转移的合规性
 * 3. 支持策略版本管理和A/B测试
 * 4. 根据全局状态、时间等因素动态调整策略
 */
interface PolicyEngine {
    /**
     * 注册策略
     * 
     * @param policy 策略对象
     */
    suspend fun registerPolicy(policy: TransitionPolicy)
    
    /**
     * 更新策略（热更新）
     * 
     * @param policyId 策略ID
     * @param policy 新策略
     */
    suspend fun updatePolicy(policyId: String, policy: TransitionPolicy)
    
    /**
     * 注销策略
     * 
     * @param policyId 策略ID
     */
    suspend fun unregisterPolicy(policyId: String)
    
    /**
     * 启用/禁用策略
     * 
     * @param policyId 策略ID
     * @param enabled 是否启用
     */
    suspend fun setPolicyEnabled(policyId: String, enabled: Boolean)
    
    /**
     * 评估状态转移的合规性
     * 
     * @param fromState 源状态
     * @param toState 目标状态
     * @param event 触发事件
     * @param context 状态上下文
     * @return 合规性分数（0.0-1.0），越高表示越符合策略
     */
    suspend fun evaluateTransitionCompliance(
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext
    ): Double
    
    /**
     * 评估多个候选转移的策略符合度
     * 
     * @param currentState 当前状态
     * @param event 触发事件
     * @param context 状态上下文
     * @param candidateStates 候选状态列表
     * @return 策略决策结果列表
     */
    suspend fun evaluateTransitions(
        currentState: State,
        event: Event,
        context: StateContext,
        candidateStates: Collection<State>
    ): List<PolicyDecision>
    
    /**
     * 获取适用于当前上下文的策略
     * 
     * @param context 状态上下文
     * @return 适用的策略列表
     */
    suspend fun getApplicablePolicies(context: StateContext): List<TransitionPolicy>
    
    /**
     * 获取策略版本历史
     * 
     * @param policyId 策略ID
     * @return 策略版本列表（按时间倒序）
     */
    suspend fun getPolicyHistory(policyId: String): List<TransitionPolicy>
    
    /**
     * 回滚策略到指定版本
     * 
     * @param policyId 策略ID
     * @param version 目标版本
     * @return 是否成功
     */
    suspend fun rollbackToVersion(policyId: String, version: String): Boolean
    
    /**
     * 启用A/B测试
     * 
     * @param policyIdA 策略A的ID
     * @param policyIdB 策略B的ID
     * @param trafficSplit 流量分割比例（0.0-1.0，表示B的流量比例）
     * @param testId 测试ID
     */
    suspend fun enableABTest(
        policyIdA: String,
        policyIdB: String,
        trafficSplit: Double,
        testId: String
    )
    
    /**
     * 停止A/B测试
     * 
     * @param testId 测试ID
     */
    suspend fun stopABTest(testId: String)
    
    /**
     * 启用灰度发布
     * 
     * @param policyId 策略ID
     * @param rolloutPercentage 发布百分比（0-100）
     * @return 发布ID
     */
    suspend fun enableGradualRollout(
        policyId: String,
        rolloutPercentage: Int
    ): String
    
    /**
     * 更新灰度发布百分比
     * 
     * @param rolloutId 发布ID
     * @param rolloutPercentage 新的发布百分比
     */
    suspend fun updateGradualRollout(rolloutId: String, rolloutPercentage: Int)
    
    /**
     * 完成灰度发布（100%）
     * 
     * @param rolloutId 发布ID
     */
    suspend fun completeGradualRollout(rolloutId: String)
}

/**
 * 转移策略
 */
data class TransitionPolicy(
    /**
     * 策略ID
     */
    val id: String,
    
    /**
     * 策略名称
     */
    val name: String,
    
    /**
     * 策略描述
     */
    val description: String,
    
    /**
     * 策略版本
     */
    val version: String,
    
    /**
     * 策略规则
     */
    val rules: List<PolicyRule>,
    
    /**
     * 策略优先级（数值越大优先级越高）
     */
    val priority: Int = 0,
    
    /**
     * 是否启用
     */
    val enabled: Boolean = true,
    
    /**
     * 策略生效条件（何时应用此策略）
     */
    val condition: PolicyCondition = PolicyCondition.Always,
    
    /**
     * 策略生效时间范围（可选）
     */
    val effectiveTimeRange: TimeRange? = null,
    
    /**
     * 策略元数据
     */
    val metadata: Map<String, Any> = emptyMap(),
    
    /**
     * 创建时间
     */
    val createdAt: Instant = Instant.now(),
    
    /**
     * 更新时间
     */
    val updatedAt: Instant = Instant.now()
)

/**
 * 策略规则
 */
data class PolicyRule(
    /**
     * 规则名称
     */
    val name: String,
    
    /**
     * 规则类型
     */
    val type: RuleType,
    
    /**
     * 规则条件（何时应用此规则）
     */
    val condition: RuleCondition,
    
    /**
     * 规则动作（应用规则时执行的动作）
     */
    val action: RuleAction,
    
    /**
     * 规则优先级
     */
    val priority: Int = 0,
    
    /**
     * 规则权重（影响最终决策的权重）
     */
    val weight: Double = 1.0
)

/**
 * 规则类型
 */
enum class RuleType {
    /**
     * 允许：允许某个转移
     */
    ALLOW,
    
    /**
     * 禁止：禁止某个转移
     */
    DENY,
    
    /**
     * 推荐：推荐某个转移
     */
    RECOMMEND,
    
    /**
     * 降级：降低某个转移的优先级
     */
    DEMOTE,
    
    /**
     * 升级：提升某个转移的优先级
     */
    PROMOTE,
    
    /**
     * 修改：修改转移的某些属性
     */
    MODIFY
}

/**
 * 规则条件
 */
sealed class RuleCondition {
    /**
     * 总是应用
     */
    object Always : RuleCondition()
    
    /**
     * 基于状态的条件
     */
    data class StateBased(
        val fromStates: Set<String>? = null,
        val toStates: Set<String>? = null
    ) : RuleCondition()
    
    /**
     * 基于事件的条件
     */
    data class EventBased(
        val eventTypes: Set<String>? = null
    ) : RuleCondition()
    
    /**
     * 基于上下文的条件
     */
    data class ContextBased(
        val predicate: (StateContext) -> Boolean
    ) : RuleCondition()
    
    /**
     * 复合条件（AND/OR）
     */
    data class Composite(
        val operator: LogicalOperator,
        val conditions: List<RuleCondition>
    ) : RuleCondition()
}

/**
 * 逻辑操作符
 */
enum class LogicalOperator {
    AND, OR, NOT
}

/**
 * 规则动作
 */
sealed class RuleAction {
    /**
     * 设置合规性分数
     */
    data class SetComplianceScore(val score: Double) : RuleAction()
    
    /**
     * 调整合规性分数（加减）
     */
    data class AdjustComplianceScore(val delta: Double) : RuleAction()
    
    /**
     * 设置优先级
     */
    data class SetPriority(val priority: Int) : RuleAction()
    
    /**
     * 调整优先级
     */
    data class AdjustPriority(val delta: Int) : RuleAction()
    
    /**
     * 自定义动作
     */
    data class Custom(val action: suspend (PolicyRule, StateContext) -> Double) : RuleAction()
}

/**
 * 策略条件（策略何时生效）
 */
sealed class PolicyCondition {
    /**
     * 总是生效
     */
    object Always : PolicyCondition()
    
    /**
     * 基于全局状态的条件
     */
    data class GlobalStateBased(
        val predicate: (Map<String, Any>) -> Boolean
    ) : PolicyCondition()
    
    /**
     * 基于时间的条件
     */
    data class TimeBased(
        val timeWindow: TimeRange
    ) : PolicyCondition()
    
    /**
     * 复合条件
     */
    data class Composite(
        val operator: LogicalOperator,
        val conditions: List<PolicyCondition>
    ) : PolicyCondition()
}

/**
 * 时间范围
 */
data class TimeRange(
    val start: Instant,
    val end: Instant?
)

/**
 * 策略决策结果
 */
data class PolicyDecision(
    /**
     * 策略ID
     */
    val policyId: String,
    
    /**
     * 目标状态ID
     */
    val targetStateId: StateId,
    
    /**
     * 目标状态
     */
    val targetState: State,
    
    /**
     * 合规性分数
     */
    val confidence: Double,
    
    /**
     * 优先级分数
     */
    val priorityScore: Double,
    
    /**
     * 决策理由
     */
    val reasoning: String,
    
    /**
     * 应用的规则
     */
    val appliedRules: List<String> = emptyList()
)

/**
 * 策略引擎实现
 */
class PolicyEngineImpl(
    private val config: PolicyConfig = PolicyConfig()
) : PolicyEngine {
    
    private val policies = ConcurrentHashMap<String, TransitionPolicy>()
    private val policyVersions = ConcurrentHashMap<String, MutableList<TransitionPolicy>>()
    private val versionManager = PolicyVersionManager()
    
    override suspend fun registerPolicy(policy: TransitionPolicy) {
        policies[policy.id] = policy
        
        // 记录策略版本历史
        versionManager.recordVersion(policy.id, policy)
        policyVersions.computeIfAbsent(policy.id) { mutableListOf() }
            .add(policy)
    }
    
    override suspend fun updatePolicy(policyId: String, policy: TransitionPolicy) {
        val oldPolicy = policies[policyId]
        if (oldPolicy != null) {
            // 保留旧版本
            versionManager.recordVersion(policyId, oldPolicy)
            policyVersions.getOrPut(policyId) { mutableListOf() }.add(oldPolicy)
        }
        
        // 更新策略
        val updatedPolicy = policy.copy(updatedAt = Instant.now())
        policies[policyId] = updatedPolicy
        versionManager.recordVersion(policyId, updatedPolicy)
    }
    
    override suspend fun unregisterPolicy(policyId: String) {
        policies.remove(policyId)
    }
    
    override suspend fun setPolicyEnabled(policyId: String, enabled: Boolean) {
        policies[policyId]?.let { policy ->
            policies[policyId] = policy.copy(enabled = enabled)
        }
    }
    
    override suspend fun evaluateTransitionCompliance(
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext
    ): Double = withContext(Dispatchers.Default) {
        val applicablePolicies = getApplicablePolicies(context)
            .filter { it.enabled }
        
        if (applicablePolicies.isEmpty()) {
            return@withContext 0.5 // 中性分数
        }
        
        var totalScore = 0.0
        var totalWeight = 0.0
        
        applicablePolicies.forEach { policy ->
            val policyScore = evaluatePolicy(policy, fromState, toState, event, context)
            val policyWeight = policy.priority.toDouble()
            
            totalScore += policyScore * policyWeight
            totalWeight += policyWeight
        }
        
        if (totalWeight > 0) {
            (totalScore / totalWeight).coerceIn(0.0, 1.0)
        } else {
            0.5
        }
    }
    
    override suspend fun evaluateTransitions(
        currentState: State,
        event: Event,
        context: StateContext,
        candidateStates: Collection<State>
    ): List<PolicyDecision> = withContext(Dispatchers.Default) {
        val applicablePolicies = getApplicablePolicies(context)
            .filter { it.enabled }
        
        candidateStates.map { targetState ->
            val complianceScore = evaluateTransitionCompliance(
                currentState,
                targetState,
                event,
                context
            )
            
            val appliedRules = mutableListOf<String>()
            var priorityScore = 0.0
            val reasoning = buildReasoning(applicablePolicies, currentState, targetState, event, context, appliedRules)
            
            PolicyDecision(
                policyId = applicablePolicies.maxByOrNull { it.priority }?.id ?: "",
                targetStateId = targetState.id,
                targetState = targetState,
                confidence = complianceScore,
                priorityScore = priorityScore,
                reasoning = reasoning,
                appliedRules = appliedRules
            )
        }
    }
    
    override suspend fun getApplicablePolicies(context: StateContext): List<TransitionPolicy> {
        val basePolicies = policies.values.filter { policy ->
            policy.enabled && isPolicyApplicable(policy, context)
        }
        
        // 检查A/B测试和灰度发布
        val finalPolicies = mutableListOf<TransitionPolicy>()
        
        basePolicies.forEach { policy ->
            // 检查是否有A/B测试
            val abTests = versionManager.getAllABTests()
            val applicableABTest = abTests.find { 
                it.enabled && (it.policyIdA == policy.id || it.policyIdB == policy.id)
            }
            
            if (applicableABTest != null) {
                val contextHash = context.hashCode()
                val selectedPolicyId = versionManager.selectABTestPolicy(applicableABTest.testId, contextHash)
                
                if (selectedPolicyId == policy.id) {
                    finalPolicies.add(policy)
                } else if (selectedPolicyId != null) {
                    // 添加另一个策略
                    policies[selectedPolicyId]?.let { finalPolicies.add(it) }
                }
            } else {
                // 检查灰度发布
                val shouldApply = versionManager.shouldApplyGradualRollout(policy.id, context.hashCode())
                if (shouldApply) {
                    finalPolicies.add(policy)
                }
            }
        }
        
        return finalPolicies.ifEmpty { basePolicies }  // 如果没有特殊配置，返回基础策略
    }
    
    override suspend fun getPolicyHistory(policyId: String): List<TransitionPolicy> {
        return versionManager.getPolicyHistory(policyId)
    }
    
    override suspend fun rollbackToVersion(policyId: String, version: String): Boolean {
        val targetPolicy = versionManager.findVersion(policyId, version)
        if (targetPolicy != null) {
            policies[policyId] = targetPolicy.copy(updatedAt = Instant.now())
            versionManager.recordVersion(policyId, targetPolicy)
            return true
        }
        return false
    }
    
    override suspend fun enableABTest(
        policyIdA: String,
        policyIdB: String,
        trafficSplit: Double,
        testId: String
    ) {
        versionManager.enableABTest(policyIdA, policyIdB, trafficSplit, testId)
    }
    
    override suspend fun stopABTest(testId: String) {
        versionManager.stopABTest(testId)
    }
    
    override suspend fun enableGradualRollout(
        policyId: String,
        rolloutPercentage: Int
    ): String {
        return versionManager.enableGradualRollout(policyId, rolloutPercentage)
    }
    
    override suspend fun updateGradualRollout(rolloutId: String, rolloutPercentage: Int) {
        versionManager.updateGradualRollout(rolloutId, rolloutPercentage)
    }
    
    override suspend fun completeGradualRollout(rolloutId: String) {
        versionManager.completeGradualRollout(rolloutId)
    }
    
    // ========== 私有辅助方法 ==========
    
    private suspend fun evaluatePolicy(
        policy: TransitionPolicy,
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext
    ): Double {
        val applicableRules = policy.rules.filter { rule ->
            isRuleApplicable(rule, fromState, toState, event, context)
        }
        
        if (applicableRules.isEmpty()) {
            return 0.5 // 中性分数
        }
        
        var totalScore = 0.0
        var totalWeight = 0.0
        
        applicableRules.forEach { rule ->
            val ruleScore = evaluateRule(rule, fromState, toState, event, context)
            totalScore += ruleScore * rule.weight
            totalWeight += rule.weight
        }
        
        return if (totalWeight > 0) {
            (totalScore / totalWeight).coerceIn(0.0, 1.0)
        } else {
            0.5
        }
    }
    
    private suspend fun evaluateRule(
        rule: PolicyRule,
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext
    ): Double {
        return when (rule.action) {
            is RuleAction.SetComplianceScore -> rule.action.score
            is RuleAction.AdjustComplianceScore -> 0.5 + rule.action.delta // 简化实现
            is RuleAction.SetPriority -> rule.action.priority / 10.0 // 归一化
            is RuleAction.AdjustPriority -> 0.5 // 简化实现
            is RuleAction.Custom -> rule.action.action(rule, context)
        }
    }
    
    private fun isRuleApplicable(
        rule: PolicyRule,
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext
    ): Boolean {
        return when (val condition = rule.condition) {
            is RuleCondition.Always -> true
            is RuleCondition.StateBased -> {
                (condition.fromStates == null || fromState.id in condition.fromStates) &&
                (condition.toStates == null || toState.id in condition.toStates)
            }
            is RuleCondition.EventBased -> {
                condition.eventTypes == null || event.type.toString() in condition.eventTypes
            }
            is RuleCondition.ContextBased -> condition.predicate(context)
            is RuleCondition.Composite -> {
                when (condition.operator) {
                    LogicalOperator.AND -> condition.conditions.all { 
                        isRuleApplicable(
                            rule.copy(condition = it),
                            fromState,
                            toState,
                            event,
                            context
                        )
                    }
                    LogicalOperator.OR -> condition.conditions.any {
                        isRuleApplicable(
                            rule.copy(condition = it),
                            fromState,
                            toState,
                            event,
                            context
                        )
                    }
                    LogicalOperator.NOT -> !isRuleApplicable(
                        rule.copy(condition = condition.conditions.first()),
                        fromState,
                        toState,
                        event,
                        context
                    )
                }
            }
        }
    }
    
    private fun isPolicyApplicable(
        policy: TransitionPolicy,
        context: StateContext
    ): Boolean {
        // 检查时间范围
        policy.effectiveTimeRange?.let { range ->
            val now = Instant.now()
            if (now.isBefore(range.start) || 
                (range.end != null && now.isAfter(range.end))) {
                return false
            }
        }
        
        // 检查策略条件
        return when (val condition = policy.condition) {
            is PolicyCondition.Always -> true
            is PolicyCondition.GlobalStateBased -> condition.predicate(context.globalState)
            is PolicyCondition.TimeBased -> {
                val now = Instant.now()
                now.isAfter(condition.timeWindow.start) &&
                (condition.timeWindow.end == null || now.isBefore(condition.timeWindow.end))
            }
            is PolicyCondition.Composite -> {
                when (condition.operator) {
                    LogicalOperator.AND -> condition.conditions.all { 
                        isPolicyApplicable(policy.copy(condition = it), context)
                    }
                    LogicalOperator.OR -> condition.conditions.any {
                        isPolicyApplicable(policy.copy(condition = it), context)
                    }
                    LogicalOperator.NOT -> !isPolicyApplicable(
                        policy.copy(condition = condition.conditions.first()),
                        context
                    )
                }
            }
        }
    }
    
    private suspend fun buildReasoning(
        policies: List<TransitionPolicy>,
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext,
        appliedRules: MutableList<String>
    ): String {
        val reasons = mutableListOf<String>()
        
        policies.forEach { policy ->
            policy.rules.forEach { rule ->
                if (isRuleApplicable(rule, fromState, toState, event, context)) {
                    appliedRules.add("${policy.name}.${rule.name}")
                    reasons.add("策略${policy.name}的规则${rule.name}适用")
                }
            }
        }
        
        return reasons.joinToString("; ") ?: "无适用的策略规则"
    }
}

/**
 * 策略配置
 */
data class PolicyConfig(
    /**
     * 默认合规性分数（当没有策略时）
     */
    val defaultComplianceScore: Double = 0.5
)

/**
 * 策略引擎工厂
 */
object PolicyEngineFactory {
    fun create(config: PolicyConfig = PolicyConfig()): PolicyEngine = PolicyEngineImpl(config)
}


