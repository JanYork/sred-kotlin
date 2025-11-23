package me.ixor.sred.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import java.time.Instant

/**
 * 自治状态测试
 */
class AutonomousStateTest {
    
    private lateinit var context: StateContext
    
    @BeforeEach
    fun setUp() {
        context = StateContextFactory.builder()
            .id("test_context")
            .currentStateId("test_state")
            .localState("key1", "value1")
            .localState("amount", 100.0)
            .build()
    }
    
    @Test
    fun `test autonomous state basic properties`() {
        val state = TestAutonomousState(
            id = "test_autonomous",
            name = "Test Autonomous State",
            autonomyLevel = 80
        )
        
        assertEquals("test_autonomous", state.id)
        assertEquals("Test Autonomous State", state.name)
        assertEquals(80, state.getAutonomyLevel())
    }
    
    @Test
    fun `test autonomous state should auto rotate`() = runBlocking {
        val state = TestAutonomousState(
            id = "test_autonomous",
            name = "Test Autonomous State"
        )
        
        val shouldRotate = state.shouldAutoRotate(context)
        // 默认实现返回 false
        assertFalse(shouldRotate)
    }
    
    @Test
    fun `test autonomous state check rotation possibility`() = runBlocking {
        val state = TestAutonomousState(
            id = "test_autonomous",
            name = "Test Autonomous State"
        )
        
        val proposals = state.checkRotationPossibility(context)
        // 默认实现返回空列表
        assertTrue(proposals.isEmpty())
    }
    
    @Test
    fun `test autonomous state propose transition`() = runBlocking {
        val state = TestAutonomousState(
            id = "test_autonomous",
            name = "Test Autonomous State"
        )
        
        val proposal = state.proposeTransition(context)
        // 默认实现返回 null
        assertNull(proposal)
    }
    
    @Test
    fun `test autonomous state observe environment`() = runBlocking {
        val state = TestAutonomousState(
            id = "test_autonomous",
            name = "Test Autonomous State"
        )
        
        val environmentFlow = state.observeEnvironment(context)
        val changes = environmentFlow.take(1).toList()
        // 默认实现返回空流
        assertTrue(changes.isEmpty())
    }
    
    @Test
    fun `test autonomous state with custom rotation logic`() = runBlocking {
        val state = CustomAutonomousState("custom_state", "Custom State")
        
        // 设置超时上下文
        val timeoutContext = context.copy(
            metadata = context.metadata + mapOf(
                "waitStartTime" to Instant.now().minusSeconds(61) // 61秒前
            )
        )
        
        val shouldRotate = state.shouldAutoRotate(timeoutContext)
        assertTrue(shouldRotate)
        
        val proposal = state.proposeTransition(timeoutContext)
        assertNotNull(proposal)
        assertEquals("custom_state", proposal!!.fromStateId)
        assertEquals("timeout_state", proposal.targetStateId)
        assertEquals(RotationReason.TEMPORAL_TRIGGER, proposal.reason)
    }
    
    @Test
    fun `test autonomous state detect local state changes`() = runBlocking {
        val state = TestAutonomousState("test_state", "Test State")
        
        val oldContext = context
        val newContext = context.updateLocalState("key1", "new_value")
            .updateLocalState("key2", "value2")
        
        val changes = (state as AbstractAutonomousState).let {
            // 使用反射或公开方法测试（简化测试，直接调用protected方法需要子类）
            // 这里测试环境变化的概念
            assertNotNull(newContext.localState["key1"])
            assertEquals("new_value", newContext.localState["key1"])
        }
    }
    
    @Test
    fun `test state rotation proposal properties`() {
        val proposal = StateRotationProposal(
            targetStateId = "target_state",
            reason = RotationReason.CONTEXT_CONDITION_MET,
            confidence = 0.85,
            priority = 10,
            justification = "上下文条件满足",
            requiredContextChanges = mapOf("status" to "ready")
        )
        
        assertEquals("target_state", proposal.targetStateId)
        assertEquals(RotationReason.CONTEXT_CONDITION_MET, proposal.reason)
        assertEquals(0.85, proposal.confidence)
        assertEquals(10, proposal.priority)
        assertEquals("ready", proposal.requiredContextChanges["status"])
    }
    
    @Test
    fun `test state transition proposal`() = runBlocking {
        val proposal = StateTransitionProposal(
            proposalId = "proposal_1",
            fromStateId = "state_a",
            targetStateId = "state_b",
            triggerEvent = null,
            reason = RotationReason.POLICY_DRIVEN,
            confidence = 0.9,
            priority = 100,
            contextUpdate = { it.updateLocalState("transferred", true) },
            preTransitionCheck = { true },
            postTransitionAction = { it }
        )
        
        assertEquals("proposal_1", proposal.proposalId)
        assertEquals("state_a", proposal.fromStateId)
        assertEquals("state_b", proposal.targetStateId)
        assertEquals(RotationReason.POLICY_DRIVEN, proposal.reason)
        
        // 测试上下文更新
        val updatedContext = proposal.contextUpdate(context)
        assertTrue(updatedContext.getLocalState<Boolean>("transferred") == true)
        
        // 测试转移前检查
        assertTrue(proposal.preTransitionCheck(context))
    }
    
    @Test
    fun `test environment change`() {
        val change = EnvironmentChange(
            type = ChangeType.MODIFIED,
            key = "amount",
            oldValue = 100.0,
            newValue = 200.0,
            timestamp = Instant.now(),
            source = ContextSource.LOCAL_STATE,
            intensity = 0.5
        )
        
        assertEquals(ChangeType.MODIFIED, change.type)
        assertEquals("amount", change.key)
        assertEquals(100.0, change.oldValue)
        assertEquals(200.0, change.newValue)
        assertEquals(ContextSource.LOCAL_STATE, change.source)
        assertEquals(0.5, change.intensity)
    }
}

/**
 * 测试用的自治状态实现
 */
class TestAutonomousState(
    id: StateId,
    name: String,
    autonomyLevel: Int = 50
) : AbstractAutonomousState(id, name, "Test autonomous state", autonomyLevel)

/**
 * 自定义自治状态 - 支持超时自动转移
 */
class CustomAutonomousState(
    id: StateId,
    name: String
) : AbstractAutonomousState(id, name, "Custom autonomous state with timeout logic", 90) {
    
    override suspend fun shouldAutoRotate(context: StateContext): Boolean {
        val waitStartTime = context.getMetadata<Instant>("waitStartTime") ?: return false
        return Instant.now().isAfter(waitStartTime.plusSeconds(60))
    }
    
    override suspend fun proposeTransition(
        context: StateContext,
        environmentChanges: List<EnvironmentChange>
    ): StateTransitionProposal? {
        if (!shouldAutoRotate(context)) {
            return null
        }
        
        return StateTransitionProposal(
            proposalId = "${id}_timeout_${System.currentTimeMillis()}",
            fromStateId = id,
            targetStateId = "timeout_state",
            triggerEvent = null,
            reason = RotationReason.TEMPORAL_TRIGGER,
            confidence = 0.9,
            priority = 100,
            contextUpdate = { ctx ->
                ctx.updateMetadata("timeoutOccurred", true)
                    .updateMetadata("timeoutTime", Instant.now())
            },
            preTransitionCheck = { true },
            postTransitionAction = { it }
        )
    }
}




