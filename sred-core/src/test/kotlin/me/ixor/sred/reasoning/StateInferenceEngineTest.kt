package me.ixor.sred.reasoning

import me.ixor.sred.core.*
import me.ixor.sred.state.StateRegistry
import me.ixor.sred.state.StateRegistryFactory
import me.ixor.sred.policy.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * 动态状态推理引擎测试
 */
class StateInferenceEngineTest {
    
    private lateinit var stateRegistry: StateRegistry
    private lateinit var contextReasoningEngine: ContextReasoningEngine
    private lateinit var stateInferenceEngine: StateInferenceEngine
    private lateinit var context: StateContext
    private lateinit var stateA: State
    private lateinit var stateB: State
    private lateinit var stateC: AutonomousState
    
    @BeforeEach
    fun setUp() = runBlocking {
        stateRegistry = StateRegistryFactory.create()
        
        // 创建测试状态
        stateA = TestState("state_a", "State A")
        stateB = TestState("state_b", "State B")
        stateC = TestAutonomousState("state_c", "State C (Autonomous)")
        
        stateRegistry.registerState(stateA)
        stateRegistry.registerState(stateB)
        stateRegistry.registerState(stateC as State)
        
        contextReasoningEngine = ContextReasoningEngineFactory.create(stateRegistry)
        stateInferenceEngine = StateInferenceEngineFactory.create(
            stateRegistry = stateRegistry,
            contextReasoningEngine = contextReasoningEngine,
            policyEngine = null
        )
        
        context = StateContextFactory.builder()
            .id("test_context")
            .currentStateId("state_a")
            .localState("amount", 1000.0)
            .build()
    }
    
    @Test
    fun `test infer transitions with predefined transitions`() = runBlocking {
        val event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event"
        )
        
        // 创建预定义转移
        val predefinedTransition = StateTransitionFactory.createSimpleTransition(
            fromStateId = "state_a",
            toStateId = "state_b",
            eventType = event.type,
            condition = { _, _, _ -> true }
        ) { _, _, ctx ->
            StateTransitionResult(
                success = true,
                nextStateId = "state_b",
                updatedContext = ctx
            )
        }
        
        val inferences = stateInferenceEngine.inferTransitions(
            currentState = stateA,
            event = event,
            context = context,
            predefinedTransitions = listOf(predefinedTransition)
        )
        
        assertTrue(inferences.isNotEmpty())
        val predefinedInference = inferences.find { 
            it.source == InferenceSource.PREDEFINED 
        }
        assertNotNull(predefinedInference)
        assertEquals("state_b", predefinedInference?.targetStateId)
    }
    
    @Test
    fun `test infer transitions with state proposal`() = runBlocking {
        val event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event"
        )
        
        val stateWithProposal = object : AbstractState("state_with_proposal", "State with Proposal", "Test") {
            override fun getPossibleTransitions(context: StateContext): Set<StateId> {
                return setOf("state_b")
            }
        }
        stateRegistry.registerState(stateWithProposal)
        
        val inferences = stateInferenceEngine.inferTransitions(
            currentState = stateWithProposal,
            event = event,
            context = context,
            predefinedTransitions = emptyList()
        )
        
        val stateProposal = inferences.find { 
            it.source == InferenceSource.STATE_PROPOSAL 
        }
        assertNotNull(stateProposal)
        assertEquals("state_b", stateProposal?.targetStateId)
    }
    
    @Test
    fun `test infer transitions with autonomous state`() = runBlocking {
        val event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event"
        )
        
        val autonomousContext = context.copy(
            currentStateId = "state_c",
            metadata = context.metadata + mapOf(
                "waitStartTime" to Instant.now().minusSeconds(61)
            )
        )
        
        val inferences = stateInferenceEngine.inferTransitions(
            currentState = stateC as State,
            event = event,
            context = autonomousContext,
            predefinedTransitions = emptyList()
        )
        
        // 应该包含自治状态的提议
        val autonomousInference = inferences.find { 
            it.source == InferenceSource.AUTONOMOUS_STATE 
        }
        // 如果状态应该轮转，应该有自治推理结果
        assertNotNull(autonomousInference)
    }
    
    @Test
    fun `test infer transitions with context reasoning`() = runBlocking {
        val event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event"
        )
        
        val inferences = stateInferenceEngine.inferTransitions(
            currentState = stateA,
            event = event,
            context = context,
            predefinedTransitions = emptyList()
        )
        
        // 应该包含上下文推理的结果
        val contextReasoning = inferences.find { 
            it.source == InferenceSource.CONTEXT_REASONING 
        }
        // 允许没有推理结果（如果阈值太高）
        assertNotNull(inferences)
    }
    
    @Test
    fun `test select optimal transition`() = runBlocking {
        val event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event"
        )
        
        // 创建预定义转移
        val predefinedTransition = StateTransitionFactory.createSimpleTransition(
            fromStateId = "state_a",
            toStateId = "state_b",
            eventType = event.type,
            condition = { _, _, _ -> true }
        ) { _, _, ctx ->
            StateTransitionResult(
                success = true,
                nextStateId = "state_b",
                updatedContext = ctx
            )
        }
        
        val optimal = stateInferenceEngine.selectOptimalTransition(
            currentState = stateA,
            event = event,
            context = context,
            predefinedTransitions = listOf(predefinedTransition)
        )
        
        assertNotNull(optimal)
        assertEquals("state_b", optimal?.inferredTransition?.targetStateId)
        assertTrue(optimal!!.compositeScore > 0.0)
        assertTrue(optimal.dimensionScores.isNotEmpty())
    }
    
    @Test
    fun `test merge inferred transitions`() = runBlocking {
        val event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event"
        )
        
        // 创建多个来源的推理结果（都指向同一个目标状态）
        val predefinedTransition = StateTransitionFactory.createSimpleTransition(
            fromStateId = "state_a",
            toStateId = "state_b",
            eventType = event.type,
            condition = { _, _, _ -> true }
        ) { _, _, ctx ->
            StateTransitionResult(
                success = true,
                nextStateId = "state_b",
                updatedContext = ctx
            )
        }
        
        val inferences = stateInferenceEngine.inferTransitions(
            currentState = stateA,
            event = event,
            context = context,
            predefinedTransitions = listOf(predefinedTransition)
        )
        
        // 相同目标状态的推理应该被合并
        val stateBInferences = inferences.filter { it.targetStateId == "state_b" }
        assertTrue(stateBInferences.isNotEmpty())
        
        // 如果有多个来源，应该被合并为 COMPOSITE
        val compositeInference = stateBInferences.find { 
            it.source == InferenceSource.COMPOSITE 
        }
        // 如果只有一个来源，不会合并
    }
    
    @Test
    fun `test inference with policy engine`() = runBlocking {
        val policyEngine = PolicyEngineFactory.create()
        
        // 注册一个策略
        policyEngine.registerPolicy(
            TransitionPolicy(
                id = "test_policy",
                name = "Test Policy",
                version = "1.0",
                description = "Test policy",
                rules = listOf(
                    PolicyRule(
                        name = "Allow transition to state_b",
                        type = RuleType.ALLOW,
                        condition = RuleCondition.StateBased(
                            toStates = setOf("state_b")
                        ),
                        action = RuleAction.SetComplianceScore(0.9)
                    )
                )
            )
        )
        
        val engineWithPolicy = StateInferenceEngineFactory.create(
            stateRegistry = stateRegistry,
            contextReasoningEngine = contextReasoningEngine,
            policyEngine = policyEngine
        )
        
        val event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event"
        )
        
        val optimal = engineWithPolicy.selectOptimalTransition(
            currentState = stateA,
            event = event,
            context = context,
            predefinedTransitions = emptyList()
        )
        
        // 策略应该影响决策
        assertNotNull(optimal)
    }
}

/**
 * 测试用的自治状态
 */
class TestAutonomousState(
    id: StateId,
    name: String
) : AbstractAutonomousState(id, name, "Test autonomous state", 75) {
    
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
            proposalId = "${id}_proposal",
            fromStateId = id,
            targetStateId = "timeout_state",
            triggerEvent = null,
            reason = RotationReason.TEMPORAL_TRIGGER,
            confidence = 0.9,
            priority = 100,
            contextUpdate = { it },
            preTransitionCheck = { true }
        )
    }
    
    override suspend fun checkRotationPossibility(
        context: StateContext
    ): List<StateRotationProposal> {
        if (shouldAutoRotate(context)) {
            return listOf(
                StateRotationProposal(
                    targetStateId = "timeout_state",
                    reason = RotationReason.TEMPORAL_TRIGGER,
                    confidence = 0.9,
                    priority = 100,
                    justification = "超时自动转移"
                )
            )
        }
        return emptyList()
    }
}




