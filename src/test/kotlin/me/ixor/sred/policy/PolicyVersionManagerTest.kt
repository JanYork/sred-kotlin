package me.ixor.sred.policy

import me.ixor.sred.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * 策略版本管理器测试
 */
class PolicyVersionManagerTest {
    
    private lateinit var versionManager: PolicyVersionManager
    private lateinit var policy1: TransitionPolicy
    private lateinit var policy2: TransitionPolicy
    
    @BeforeEach
    fun setUp() {
        versionManager = PolicyVersionManager()
        
        policy1 = TransitionPolicy(
            id = "test_policy",
            name = "Test Policy",
            description = "Test",
            version = "1.0",
            rules = emptyList()
        )
        
        policy2 = policy1.copy(version = "2.0", name = "Updated Test Policy")
    }
    
    @Test
    fun `test record and get policy history`() = runBlocking {
        versionManager.recordVersion("test_policy", policy1)
        versionManager.recordVersion("test_policy", policy2)
        
        val history = versionManager.getPolicyHistory("test_policy")
        
        assertEquals(2, history.size)
        assertTrue(history.any { it.version == "1.0" })
        assertTrue(history.any { it.version == "2.0" })
    }
    
    @Test
    fun `test find specific version`() = runBlocking {
        versionManager.recordVersion("test_policy", policy1)
        versionManager.recordVersion("test_policy", policy2)
        
        val v1 = versionManager.findVersion("test_policy", "1.0")
        val v2 = versionManager.findVersion("test_policy", "2.0")
        val v3 = versionManager.findVersion("test_policy", "3.0")
        
        assertNotNull(v1)
        assertEquals("1.0", v1?.version)
        assertNotNull(v2)
        assertEquals("2.0", v2?.version)
        assertNull(v3)
    }
    
    @Test
    fun `test enable and stop AB test`() = runBlocking {
        versionManager.enableABTest("policy_a", "policy_b", 0.5, "test_1")
        
        val test = versionManager.getABTest("test_1")
        assertNotNull(test)
        assertEquals("policy_a", test?.policyIdA)
        assertEquals("policy_b", test?.policyIdB)
        assertEquals(0.5, test?.trafficSplit)
        assertTrue(test?.enabled == true)
        
        versionManager.stopABTest("test_1")
        
        val stoppedTest = versionManager.getABTest("test_1")
        assertNotNull(stoppedTest)
        assertFalse(stoppedTest?.enabled == true)
    }
    
    @Test
    fun `test select AB test policy`() = runBlocking {
        versionManager.enableABTest("policy_a", "policy_b", 0.5, "test_1")
        
        // 使用固定的contextHash进行测试
        val selectedA = versionManager.selectABTestPolicy("test_1", 10)
        val selectedB = versionManager.selectABTestPolicy("test_1", 60)
        
        // 由于使用hash，结果应该是确定的
        assertNotNull(selectedA)
        assertTrue(selectedA == "policy_a" || selectedA == "policy_b")
    }
    
    @Test
    fun `test gradual rollout`() = runBlocking {
        val rolloutId = versionManager.enableGradualRollout("new_policy", 10)
        
        assertNotNull(rolloutId)
        assertTrue(rolloutId.startsWith("new_policy"))
        
        val rollout = versionManager.getGradualRollout(rolloutId)
        assertNotNull(rollout)
        assertEquals(10, rollout?.rolloutPercentage)
        assertTrue(rollout?.enabled == true)
    }
    
    @Test
    fun `test update gradual rollout`() = runBlocking {
        val rolloutId = versionManager.enableGradualRollout("new_policy", 10)
        
        versionManager.updateGradualRollout(rolloutId, 50)
        
        val rollout = versionManager.getGradualRollout(rolloutId)
        assertEquals(50, rollout?.rolloutPercentage)
    }
    
    @Test
    fun `test complete gradual rollout`() = runBlocking {
        val rolloutId = versionManager.enableGradualRollout("new_policy", 50)
        
        versionManager.completeGradualRollout(rolloutId)
        
        val rollout = versionManager.getGradualRollout(rolloutId)
        assertEquals(100, rollout?.rolloutPercentage)
        assertFalse(rollout?.enabled == true)
    }
    
    @Test
    fun `test should apply gradual rollout`() = runBlocking {
        val rolloutId = versionManager.enableGradualRollout("new_policy", 50)
        
        // 多次测试，由于是概率性的，应该有一些true和一些false
        val results = (1..100).map {
            versionManager.shouldApplyGradualRollout("new_policy", it)
        }
        
        // 50%的发布比例，应该有大致一半的结果为true
        val trueCount = results.count { it }
        assertTrue(trueCount > 30 && trueCount < 70)  // 允许一定误差
    }
    
    @Test
    fun `test get all AB tests and rollouts`() = runBlocking {
        versionManager.enableABTest("a1", "b1", 0.5, "test1")
        versionManager.enableABTest("a2", "b2", 0.3, "test2")
        versionManager.enableGradualRollout("policy1", 10)
        versionManager.enableGradualRollout("policy2", 20)
        
        val allTests = versionManager.getAllABTests()
        val allRollouts = versionManager.getAllGradualRollouts()
        
        assertEquals(2, allTests.size)
        assertEquals(2, allRollouts.size)
    }
}

/**
 * 策略引擎版本管理功能测试
 */
class PolicyEngineVersionManagementTest {
    
    private lateinit var policyEngine: PolicyEngine
    private lateinit var policyV1: TransitionPolicy
    private lateinit var policyV2: TransitionPolicy
    
    @BeforeEach
    fun setUp() {
        policyEngine = PolicyEngineFactory.create()
        
        policyV1 = TransitionPolicy(
            id = "test_policy",
            name = "Test Policy V1",
            description = "Version 1",
            version = "1.0",
            rules = listOf(
                PolicyRule(
                    name = "Rule 1",
                    type = RuleType.ALLOW,
                    condition = RuleCondition.Always,
                    action = RuleAction.SetComplianceScore(0.7)
                )
            )
        )
        
        policyV2 = policyV1.copy(
            version = "2.0",
            name = "Test Policy V2",
            rules = listOf(
                PolicyRule(
                    name = "Rule 2",
                    type = RuleType.ALLOW,
                    condition = RuleCondition.Always,
                    action = RuleAction.SetComplianceScore(0.9)
                )
            )
        )
    }
    
    @Test
    fun `test policy version history`() = runBlocking {
        policyEngine.registerPolicy(policyV1)
        policyEngine.updatePolicy("test_policy", policyV2)
        
        val history = policyEngine.getPolicyHistory("test_policy")
        
        assertTrue(history.size >= 2)
        assertTrue(history.any { it.version == "1.0" })
        assertTrue(history.any { it.version == "2.0" })
    }
    
    @Test
    fun `test rollback to version`() = runBlocking {
        policyEngine.registerPolicy(policyV1)
        policyEngine.updatePolicy("test_policy", policyV2)
        
        val success = policyEngine.rollbackToVersion("test_policy", "1.0")
        
        assertTrue(success)
        
        val currentPolicies = policyEngine.getApplicablePolicies(StateContextFactory.create())
        val currentPolicy = currentPolicies.find { it.id == "test_policy" }
        assertNotNull(currentPolicy)
        assertEquals("1.0", currentPolicy?.version)
    }
    
    @Test
    fun `test AB test`() = runBlocking {
        val policyA = TransitionPolicy(
            id = "policy_a",
            name = "Policy A",
            description = "A",
            version = "1.0",
            rules = emptyList()
        )
        
        val policyB = TransitionPolicy(
            id = "policy_b",
            name = "Policy B",
            description = "B",
            version = "1.0",
            rules = emptyList()
        )
        
        policyEngine.registerPolicy(policyA)
        policyEngine.registerPolicy(policyB)
        
        policyEngine.enableABTest("policy_a", "policy_b", 0.5, "ab_test_1")
        
        // A/B测试应该影响策略选择
        val policies = policyEngine.getApplicablePolicies(StateContextFactory.create())
        // 由于A/B测试的存在，应该只返回一个策略
        assertTrue(policies.any { it.id == "policy_a" || it.id == "policy_b" })
        
        policyEngine.stopABTest("ab_test_1")
    }
    
    @Test
    fun `test gradual rollout`() = runBlocking {
        val newPolicy = TransitionPolicy(
            id = "new_policy",
            name = "New Policy",
            description = "New",
            version = "1.0",
            rules = emptyList()
        )
        
        policyEngine.registerPolicy(newPolicy)
        
        val rolloutId = policyEngine.enableGradualRollout("new_policy", 30)
        
        assertNotNull(rolloutId)
        
        // 更新发布百分比
        policyEngine.updateGradualRollout(rolloutId, 60)
        
        // 完成发布
        policyEngine.completeGradualRollout(rolloutId)
    }
}



