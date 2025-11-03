package me.ixor.sred.policy

import me.ixor.sred.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * 策略引擎测试
 */
class PolicyEngineTest {
    
    private lateinit var policyEngine: PolicyEngine
    private lateinit var stateA: State
    private lateinit var stateB: State
    private lateinit var context: StateContext
    private lateinit var event: Event
    
    @BeforeEach
    fun setUp() {
        policyEngine = PolicyEngineFactory.create()
        
        stateA = TestState("state_a", "State A")
        stateB = TestState("state_b", "State B")
        
        context = StateContextFactory.builder()
            .id("test_context")
            .currentStateId("state_a")
            .localState("amount", 1000.0)
            .globalState("systemLoad", 0.5)
            .build()
        
        event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event"
        )
    }
    
    @Test
    fun `test register and unregister policy`() = runBlocking {
        val policy = TransitionPolicy(
            id = "test_policy_1",
            name = "Test Policy",
            version = "1.0",
            description = "Test",
            rules = emptyList()
        )
        
        policyEngine.registerPolicy(policy)
        
        // 策略应该被注册
        val applicablePolicies = policyEngine.getApplicablePolicies(context)
        assertTrue(applicablePolicies.any { it.id == "test_policy_1" })
        
        // 注销策略
        policyEngine.unregisterPolicy("test_policy_1")
        val policiesAfterUnregister = policyEngine.getApplicablePolicies(context)
        assertFalse(policiesAfterUnregister.any { it.id == "test_policy_1" })
    }
    
    @Test
    fun `test update policy`() = runBlocking {
        val originalPolicy = TransitionPolicy(
            id = "test_policy_2",
            name = "Original Policy",
            version = "1.0",
            description = "Original",
            rules = emptyList()
        )
        
        policyEngine.registerPolicy(originalPolicy)
        
        val updatedPolicy = originalPolicy.copy(
            name = "Updated Policy",
            version = "2.0",
            description = "Updated"
        )
        
        policyEngine.updatePolicy("test_policy_2", updatedPolicy)
        
        val applicablePolicies = policyEngine.getApplicablePolicies(context)
        val policy = applicablePolicies.find { it.id == "test_policy_2" }
        assertNotNull(policy)
        assertEquals("Updated Policy", policy?.name)
        assertEquals("2.0", policy?.version)
    }
    
    @Test
    fun `test enable and disable policy`() = runBlocking {
        val policy = TransitionPolicy(
            id = "test_policy_3",
            name = "Test Policy",
            version = "1.0",
            description = "Test",
            rules = emptyList()
        )
        
        policyEngine.registerPolicy(policy)
        
        // 禁用策略
        policyEngine.setPolicyEnabled("test_policy_3", false)
        var applicablePolicies = policyEngine.getApplicablePolicies(context)
        assertFalse(applicablePolicies.any { it.id == "test_policy_3" })
        
        // 启用策略
        policyEngine.setPolicyEnabled("test_policy_3", true)
        applicablePolicies = policyEngine.getApplicablePolicies(context)
        assertTrue(applicablePolicies.any { it.id == "test_policy_3" })
    }
    
    @Test
    fun `test evaluate transition compliance with allow rule`() = runBlocking {
        val policy = TransitionPolicy(
            id = "allow_policy",
            name = "Allow Policy",
            version = "1.0",
            description = "Allow transitions to state_b",
            rules = listOf(
                PolicyRule(
                    name = "Allow to state_b",
                    type = RuleType.ALLOW,
                    condition = RuleCondition.StateBased(
                        toStates = setOf("state_b")
                    ),
                    action = RuleAction.SetComplianceScore(0.9)
                )
            )
        )
        
        policyEngine.registerPolicy(policy)
        
        val compliance = policyEngine.evaluateTransitionCompliance(
            fromState = stateA,
            toState = stateB,
            event = event,
            context = context
        )
        
        assertTrue(compliance > 0.5) // 应该得到较高的合规性分数
    }
    
    @Test
    fun `test evaluate transition compliance with deny rule`() = runBlocking {
        val policy = TransitionPolicy(
            id = "deny_policy",
            name = "Deny Policy",
            version = "1.0",
            description = "Deny transitions to state_b",
            rules = listOf(
                PolicyRule(
                    name = "Deny to state_b",
                    type = RuleType.DENY,
                    condition = RuleCondition.StateBased(
                        toStates = setOf("state_b")
                    ),
                    action = RuleAction.SetComplianceScore(0.1)
                )
            )
        )
        
        policyEngine.registerPolicy(policy)
        
        val compliance = policyEngine.evaluateTransitionCompliance(
            fromState = stateA,
            toState = stateB,
            event = event,
            context = context
        )
        
        assertTrue(compliance < 0.5) // 应该得到较低的合规性分数
    }
    
    @Test
    fun `test policy with context condition`() = runBlocking {
        val policy = TransitionPolicy(
            id = "context_policy",
            name = "Context Policy",
            version = "1.0",
            description = "Policy based on context",
            rules = listOf(
                PolicyRule(
                    name = "High amount rule",
                    type = RuleType.ALLOW,
                    condition = RuleCondition.ContextBased { ctx ->
                        (ctx.getLocalState<Number>("amount")?.toDouble() ?: 0.0) > 500.0
                    },
                    action = RuleAction.SetComplianceScore(0.95)
                )
            )
        )
        
        policyEngine.registerPolicy(policy)
        
        val compliance = policyEngine.evaluateTransitionCompliance(
            fromState = stateA,
            toState = stateB,
            event = event,
            context = context
        )
        
        // amount = 1000.0 > 500.0，应该得到高分
        assertTrue(compliance > 0.9)
    }
    
    @Test
    fun `test policy with global state condition`() = runBlocking {
        val policy = TransitionPolicy(
            id = "global_policy",
            name = "Global State Policy",
            version = "1.0",
            description = "Policy based on global state",
            condition = PolicyCondition.GlobalStateBased { globalState ->
                (globalState["systemLoad"] as? Double ?: 1.0) < 0.7
            },
            rules = listOf(
                PolicyRule(
                    name = "Low load rule",
                    type = RuleType.ALLOW,
                    condition = RuleCondition.Always,
                    action = RuleAction.SetComplianceScore(0.8)
                )
            )
        )
        
        policyEngine.registerPolicy(policy)
        
        // systemLoad = 0.5 < 0.7，策略应该生效
        val compliance = policyEngine.evaluateTransitionCompliance(
            fromState = stateA,
            toState = stateB,
            event = event,
            context = context
        )
        
        assertTrue(compliance > 0.7)
    }
    
    @Test
    fun `test policy with time range`() = runBlocking {
        val now = Instant.now()
        val policy = TransitionPolicy(
            id = "time_policy",
            name = "Time Policy",
            version = "1.0",
            description = "Policy with time range",
            effectiveTimeRange = TimeRange(
                start = now.minusSeconds(3600),
                end = now.plusSeconds(3600)
            ),
            rules = listOf(
                PolicyRule(
                    name = "Time window rule",
                    type = RuleType.ALLOW,
                    condition = RuleCondition.Always,
                    action = RuleAction.SetComplianceScore(0.85)
                )
            )
        )
        
        policyEngine.registerPolicy(policy)
        
        // 在当前时间范围内，策略应该生效
        val compliance = policyEngine.evaluateTransitionCompliance(
            fromState = stateA,
            toState = stateB,
            event = event,
            context = context
        )
        
        assertTrue(compliance > 0.8)
    }
    
    @Test
    fun `test evaluate transitions`() = runBlocking {
        val policy = TransitionPolicy(
            id = "multi_state_policy",
            name = "Multi State Policy",
            version = "1.0",
            description = "Evaluate multiple transitions",
            rules = listOf(
                PolicyRule(
                    name = "Prefer state_b",
                    type = RuleType.RECOMMEND,
                    condition = RuleCondition.StateBased(
                        toStates = setOf("state_b")
                    ),
                    action = RuleAction.SetComplianceScore(0.9)
                )
            )
        )
        
        policyEngine.registerPolicy(policy)
        
        val decisions = policyEngine.evaluateTransitions(
            currentState = stateA,
            event = event,
            context = context,
            candidateStates = listOf(stateB)
        )
        
        assertTrue(decisions.isNotEmpty())
        val decision = decisions.find { it.targetStateId == "state_b" }
        assertNotNull(decision)
        assertTrue(decision!!.confidence > 0.5)
    }
    
    @Test
    fun `test policy with multiple rules`() = runBlocking {
        val policy = TransitionPolicy(
            id = "multi_rule_policy",
            name = "Multi Rule Policy",
            version = "1.0",
            description = "Policy with multiple rules",
            rules = listOf(
                PolicyRule(
                    name = "Rule 1",
                    type = RuleType.ALLOW,
                    condition = RuleCondition.Always,
                    action = RuleAction.SetComplianceScore(0.7),
                    weight = 0.6
                ),
                PolicyRule(
                    name = "Rule 2",
                    type = RuleType.RECOMMEND,
                    condition = RuleCondition.StateBased(toStates = setOf("state_b")),
                    action = RuleAction.AdjustComplianceScore(0.2),
                    weight = 0.4
                )
            )
        )
        
        policyEngine.registerPolicy(policy)
        
        val compliance = policyEngine.evaluateTransitionCompliance(
            fromState = stateA,
            toState = stateB,
            event = event,
            context = context
        )
        
        // 多个规则应该被综合评估
        assertTrue(compliance > 0.0)
        assertTrue(compliance <= 1.0)
    }
}

/**
 * 测试用的状态
 */
class TestState(
    id: StateId,
    name: String
) : AbstractState(id, name, "Test state")



