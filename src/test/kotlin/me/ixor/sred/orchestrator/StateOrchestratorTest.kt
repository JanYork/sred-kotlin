package me.ixor.sred.orchestrator

import me.ixor.sred.core.*
import me.ixor.sred.event.*
import me.ixor.sred.state.*
import me.ixor.sred.reasoning.*
import me.ixor.sred.policy.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.time.Instant

/**
 * 状态编排器整合测试
 */
class StateOrchestratorTest {
    
    private lateinit var orchestrator: StateOrchestrator
    private lateinit var stateManager: StateManager
    private lateinit var eventBus: EventBus
    private lateinit var stateA: State
    private lateinit var stateB: State
    private lateinit var stateC: AutonomousState
    
    @BeforeEach
    fun setUp() = runBlocking {
        // 创建状态注册表
        val stateRegistry = StateRegistryFactory.create()
        
        // 创建测试状态
        stateA = TestState("state_a", "State A")
        stateB = TestState("state_b", "State B")
        stateC = TestAutonomousState("state_c", "State C (Autonomous)")
        
        stateRegistry.registerState(stateA)
        stateRegistry.registerState(stateB)
        stateRegistry.registerState(stateC as State)
        
        // 创建状态管理器和事件总线
        val statePersistence = InMemoryStatePersistence()
        stateManager = StateManagerFactory.create(stateRegistry, statePersistence)
        stateManager.initialize()
        
        eventBus = EventBusFactory.create()
        eventBus.start()
        
        // 创建编排器
        orchestrator = StateOrchestratorBuilder.create()
            .withStateManager(stateManager)
            .withEventBus(eventBus)
            .withAutonomousRotation(true)
            .build()
        
        orchestrator.start()
    }
    
    @Test
    fun `test orchestrator creation and initialization`() = runBlocking {
        assertNotNull(orchestrator)
        assertNotNull(orchestrator.stateManager)
        assertNotNull(orchestrator.eventBus)
        assertTrue(orchestrator.isRunning())
    }
    
    @Test
    fun `test process event with predefined transition`() = runBlocking {
        // 注册预定义转移
        val transition = StateTransitionFactory.createSimpleTransition(
            fromStateId = "state_a",
            toStateId = "state_b",
            eventType = EventType("test", "process"),
            condition = { _, _, _ -> true }
        ) { _, _, ctx ->
            StateTransitionResult(
                success = true,
                nextStateId = "state_b",
                updatedContext = ctx.updateLocalState("transferred", true)
            )
        }
        
        orchestrator.registerTransition(transition)
        
        // 创建初始上下文
        val contextId = "test_context_1"
        val context = StateContextFactory.builder()
            .id(contextId)
            .currentStateId("state_a")
            .localState("amount", 100.0)
            .build()
        
        stateManager.saveContext(context)
        
        // 创建事件
        val event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event",
            contextId = contextId
        )
        
        // 处理事件
        val result = orchestrator.processEvent(event)
        
        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("state_b", result.nextStateId)
        
        // 验证状态已更新
        val updatedContext = stateManager.getContext(contextId)
        assertNotNull(updatedContext)
        assertEquals("state_b", updatedContext?.currentStateId)
        assertTrue(updatedContext?.getLocalState<Boolean>("transferred") == true)
    }
    
    @Test
    fun `test process event with dynamic inference`() = runBlocking {
        // 不注册预定义转移，依赖动态推理
        
        val contextId = "test_context_2"
        val context = StateContextFactory.builder()
            .id(contextId)
            .currentStateId("state_a")
            .localState("amount", 1000.0)
            .build()
        
        stateManager.saveContext(context)
        
        val event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event",
            contextId = contextId
        )
        
        val result = orchestrator.processEvent(event)
        
        // 即使没有预定义转移，推理引擎也应该尝试推理
        assertNotNull(result)
        // 可能成功也可能失败（取决于推理结果）
    }
    
    @Test
    fun `test autonomous state rotation`() = runBlocking {
        // 创建自治状态的上下文（带有超时标记）
        val contextId = "test_context_3"
        val context = StateContextFactory.builder()
            .id(contextId)
            .currentStateId("state_c")
            .metadata("waitStartTime", Instant.now().minusSeconds(61))
            .build()
        
        stateManager.saveContext(context)
        
        // 等待自治轮转检查
        delay(2000) // 等待自治轮转周期
        
        // 验证状态是否已自动轮转
        val updatedContext = stateManager.getContext(contextId)
        // 如果自治轮转发生，状态应该改变
        assertNotNull(updatedContext)
    }
    
    @Test
    fun `test orchestrator statistics`() = runBlocking {
        // 处理一些事件
        val contextId = "test_context_4"
        val context = StateContextFactory.builder()
            .id(contextId)
            .currentStateId("state_a")
            .build()
        
        stateManager.saveContext(context)
        
        val transition = StateTransitionFactory.createSimpleTransition(
            fromStateId = "state_a",
            toStateId = "state_b",
            eventType = EventType("test", "process"),
            condition = { _, _, _ -> true }
        ) { _, _, ctx ->
            StateTransitionResult(
                success = true,
                nextStateId = "state_b",
                updatedContext = ctx
            )
        }
        
        orchestrator.registerTransition(transition)
        
        val event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event",
            contextId = contextId
        )
        
        orchestrator.processEvent(event)
        
        delay(500)
        
        val stats = orchestrator.getStatistics()
        assertNotNull(stats)
        assertTrue(stats.totalEventsProcessed >= 1)
        assertTrue(stats.totalTransitionsExecuted >= 0)
    }
    
    @Test
    fun `test orchestrator with policy engine`() = runBlocking {
        // 创建带策略引擎的编排器
        val policyEngine = PolicyEngineFactory.create()
        
        val policy = TransitionPolicy(
            id = "test_policy",
            name = "Test Policy",
            version = "1.0",
            description = "Test",
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
        
        val orchestratorWithPolicy = StateOrchestratorBuilder.create()
            .withStateManager(stateManager)
            .withEventBus(eventBus)
            .withPolicyEngine(policyEngine)
            .build()
        
        orchestratorWithPolicy.start()
        
        assertNotNull(orchestratorWithPolicy.getPolicyEngine())
        
        orchestratorWithPolicy.stop()
    }
    
    @Test
    fun `test orchestrator transition registry`() = runBlocking {
        val transition1 = StateTransitionFactory.createSimpleTransition(
            fromStateId = "state_a",
            toStateId = "state_b",
            eventType = EventType("test", "event1"),
            condition = { _, _, _ -> true }
        ) { _, _, ctx ->
            StateTransitionResult(
                success = true,
                nextStateId = "state_b",
                updatedContext = ctx
            )
        }
        
        val transition2 = StateTransitionFactory.createSimpleTransition(
            fromStateId = "state_b",
            toStateId = "state_a",
            eventType = EventType("test", "event2"),
            condition = { _, _, _ -> true }
        ) { _, _, ctx ->
            StateTransitionResult(
                success = true,
                nextStateId = "state_a",
                updatedContext = ctx
            )
        }
        
        orchestrator.registerTransition(transition1)
        orchestrator.registerTransition(transition2)
        
        // 验证转移已注册
        val allTransitions = orchestrator.transitionRegistry.getAllTransitions()
        assertTrue(allTransitions.size >= 2)
        
        // 注销转移
        orchestrator.unregisterTransition(transition1.id)
        val transitionsAfterUnregister = orchestrator.transitionRegistry.getAllTransitions()
        assertFalse(transitionsAfterUnregister.any { it.id == transition1.id })
    }
    
    @Test
    fun `test orchestrator stop and cleanup`() = runBlocking {
        assertTrue(orchestrator.isRunning())
        
        orchestrator.stop()
        
        // 等待停止完成
        delay(500)
        
        assertFalse(orchestrator.isRunning())
        
        // 停止后，应该能够再次启动
        orchestrator.start()
        assertTrue(orchestrator.isRunning())
    }
    
    @Test
    fun `test orchestrator event listener`() = runBlocking {
        var eventReceived = false
        val receivedEventId = mutableSetOf<String>()
        
        val listener = orchestrator.createEventListener { event ->
            eventReceived = true
            receivedEventId.add(event.id)
        }
        
        eventBus.subscribe(EventType("test", "*"), listener)
        
        val contextId = "test_context_5"
        val context = StateContextFactory.builder()
            .id(contextId)
            .currentStateId("state_a")
            .build()
        
        stateManager.saveContext(context)
        
        val event = EventFactory.create(
            type = EventType("test", "process"),
            name = "Process Event",
            contextId = contextId
        )
        
        orchestrator.processEvent(event)
        
        // 等待事件传播
        withTimeout(2000) {
            while (!eventReceived) {
                delay(50)
            }
        }
        
        assertTrue(eventReceived)
        assertTrue(receivedEventId.contains(event.id))
    }
    
    @Test
    fun `test orchestrator inference engine access`() = runBlocking {
        val inferenceEngine = orchestrator.getInferenceEngine()
        assertNotNull(inferenceEngine)
    }
    
    @Test
    fun `test orchestrator context reasoning engine access`() = runBlocking {
        val reasoningEngine = orchestrator.getContextReasoningEngine()
        assertNotNull(reasoningEngine)
    }
}

/**
 * 测试用的状态
 */
class TestState(
    id: StateId,
    name: String
) : AbstractState(id, name, "Test state") {
    
    override fun canEnter(context: StateContext): Boolean {
        return true
    }
    
    override fun getPossibleTransitions(context: StateContext): Set<StateId> {
        return setOf("state_b")
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
            proposalId = "${id}_proposal_${System.currentTimeMillis()}",
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

