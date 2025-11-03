package me.ixor.sred.orchestrator

import me.ixor.sred.core.*
import me.ixor.sred.event.*
import me.ixor.sred.state.*
import me.ixor.sred.reasoning.*
import me.ixor.sred.policy.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 状态调度器 - SRED架构的控制论中枢
 * 
 * 调度器是整个SRED架构的"控制论中枢"。
 * 它基于全局上下文决定何时触发何种状态转移。
 * 
 * 核心逻辑：NextState = T(CurrentState, Event, Context)
 */
interface StateOrchestrator {
    /**
     * 状态管理器
     */
    val stateManager: StateManager
    
    /**
     * 事件总线
     */
    val eventBus: EventBus
    
    /**
     * 状态转移注册表
     */
    val transitionRegistry: TransitionRegistry
    
    /**
     * 启动调度器
     */
    suspend fun start()
    
    /**
     * 停止调度器
     */
    suspend fun stop()
    
    /**
     * 处理事件
     */
    suspend fun processEvent(event: Event): OrchestrationResult
    
    /**
     * 注册状态转移
     */
    suspend fun registerTransition(transition: StateTransition)
    
    /**
     * 注销状态转移
     */
    suspend fun unregisterTransition(transitionId: TransitionId)
    
    /**
     * 获取调度器统计信息
     */
    fun getStatistics(): OrchestratorStatistics
}

/**
 * 状态转移注册表
 */
interface TransitionRegistry {
    /**
     * 注册状态转移
     */
    suspend fun registerTransition(transition: StateTransition)
    
    /**
     * 注销状态转移
     */
    suspend fun unregisterTransition(transitionId: TransitionId)
    
    /**
     * 获取状态转移
     */
    fun getTransition(transitionId: TransitionId): StateTransition?
    
    /**
     * 获取所有状态转移
     */
    fun getAllTransitions(): Collection<StateTransition>
    
    /**
     * 根据源状态和事件类型查找转移
     */
    fun findTransitions(fromStateId: StateId, eventType: EventType): List<StateTransition>
}

/**
 * 调度结果
 */
data class OrchestrationResult(
    val success: Boolean,
    val nextStateId: StateId? = null,
    val updatedContext: StateContext? = null,
    val error: Throwable? = null,
    val sideEffects: List<SideEffect> = emptyList()
)

/**
 * 调度器统计信息
 */
data class OrchestratorStatistics(
    val totalEventsProcessed: Long,
    val successfulTransitions: Long,
    val failedTransitions: Long,
    val averageProcessingTimeMs: Double,
    val lastProcessedAt: Instant?
)

/**
 * 状态编排器实现 - 整合动态推理、自治状态和策略引擎
 * 
 * 符合论文要求：
 * - "执行路径在运行时动态生成，而非编译时确定"
 * - "系统应自行知道何时、为何、如何行动"
 * - "系统策略可在运行时实时变更"
 * 
 * 此编排器整合了：
 * 1. 动态状态推理引擎（StateInferenceEngine）
 * 2. 上下文推理引擎（ContextReasoningEngine）
 * 3. 策略引擎（PolicyEngine）
 * 4. 自治状态支持（AutonomousState）
 */
class StateOrchestratorImpl(
    override val stateManager: StateManager,
    override val eventBus: EventBus,
    override val transitionRegistry: TransitionRegistry,
    private val stateInferenceEngine: StateInferenceEngine,
    private val contextReasoningEngine: ContextReasoningEngine,
    private val policyEngine: PolicyEngine? = null,
    private val enableAutonomousRotation: Boolean = true
) : StateOrchestrator {
    
    private val mutex = Mutex()
    private val statistics = OrchestratorStatisticsImpl()
    private var isRunning = false
    private var eventListener: EventListener? = null
    private var autonomousRotationJob: Job? = null
    
    override suspend fun start() {
        mutex.withLock {
            if (isRunning) return
            
            // 启动状态管理器
            stateManager.start()
            
            // 启动事件总线
            eventBus.start()
            
            // 注册事件监听器
            eventListener = createEventListener()
            eventBus.subscribe(
                eventType = EventType("system", "all"),
                listener = eventListener!!,
                filter = null
            )
            
            // 如果启用自治轮转，启动后台任务
            if (enableAutonomousRotation) {
                startAutonomousRotationMonitoring()
            }
            
            isRunning = true
        }
    }
    
    override suspend fun stop() {
        mutex.withLock {
            if (!isRunning) return
            
            // 停止自治轮转监控
            autonomousRotationJob?.cancel()
            autonomousRotationJob = null
            
            // 停止事件总线
            eventBus.stop()
            
            // 停止状态管理器
            stateManager.stop()
            
            isRunning = false
        }
    }
    
    override suspend fun processEvent(event: Event): OrchestrationResult {
        if (!isRunning) {
            return OrchestrationResult(
                success = false,
                error = IllegalStateException("Orchestrator is not running")
            )
        }
        
        val startTime = System.currentTimeMillis()
        
        try {
            val currentState = stateManager.getCurrentState()
            val currentContext = stateManager.currentContext
            
            if (currentState == null || currentContext == null) {
                return OrchestrationResult(
                    success = false,
                    error = IllegalStateException("No current state or context")
                )
            }
            
            // ========== 使用动态推理引擎 ==========
            // 1. 获取预定义转移
            val predefinedTransitions = transitionRegistry.findTransitions(
                currentState.id,
                event.type
            )
            
            // 2. 使用推理引擎动态推理最优转移
            val optimalTransition = stateInferenceEngine.selectOptimalTransition(
                currentState = currentState,
                event = event,
                context = currentContext,
                predefinedTransitions = predefinedTransitions
            )
            
            if (optimalTransition == null) {
                return OrchestrationResult(
                    success = false,
                    error = IllegalStateException(
                        "No valid transitions found for state ${currentState.id} and event ${event.type}"
                    )
                )
            }
            
            // 3. 执行推理出的最优转移
            val transitionResult = mutex.withLock {
                executeInferredTransition(
                    currentState = currentState,
                    optimalTransition = optimalTransition,
                    event = event,
                    context = currentContext
                )
            }
            
            if (transitionResult.success && transitionResult.nextStateId != null) {
                // 执行状态转移
                val stateTransitionResult = stateManager.transitionTo(
                    transitionResult.nextStateId,
                    event,
                    transitionResult.updatedContext
                )
                
                if (stateTransitionResult.success) {
                    statistics.recordSuccess()
                    return OrchestrationResult(
                        success = true,
                        nextStateId = stateTransitionResult.nextStateId,
                        updatedContext = stateTransitionResult.updatedContext,
                        sideEffects = stateTransitionResult.sideEffects
                    )
                } else {
                    statistics.recordFailure()
                    return OrchestrationResult(
                        success = false,
                        error = stateTransitionResult.error
                    )
                }
            } else {
                statistics.recordFailure()
                return OrchestrationResult(
                    success = false,
                    error = transitionResult.error
                )
            }
            
        } catch (e: Exception) {
            statistics.recordFailure()
            return OrchestrationResult(
                success = false,
                error = e
            )
        } finally {
            statistics.updateProcessingTime(System.currentTimeMillis() - startTime)
        }
    }
    
    /**
     * 执行推理出的转移
     */
    private suspend fun executeInferredTransition(
        currentState: State,
        optimalTransition: OptimalTransition,
        event: Event,
        context: StateContext
    ): StateTransitionResult {
        val inferred = optimalTransition.inferredTransition
        
        // 如果是自治状态提议，使用提议的上下文更新
        val updatedContext = if (inferred.autonomousProposal != null) {
            val proposal = inferred.autonomousProposal!!
            
            // 执行转移前检查
            if (!proposal.preTransitionCheck(context)) {
                return StateTransitionResult(
                    success = false,
                    updatedContext = context,
                    error = IllegalStateException("Pre-transition check failed")
                )
            }
            
            // 更新上下文
            val ctxAfterUpdate = proposal.contextUpdate(context)
            
            // 执行转移后动作
            proposal.postTransitionAction(ctxAfterUpdate)
        } else {
            // 普通转移，更新上下文
            context.copy(
                currentStateId = inferred.targetStateId,
                localState = context.localState + inferred.requiredContext
            ).addEvent(event)
        }
        
        return StateTransitionResult(
            success = true,
            nextStateId = inferred.targetStateId,
            updatedContext = updatedContext,
            sideEffects = emptyList()
        )
    }
    
    /**
     * 启动自治轮转监控
     * 
     * 定期检查自治状态是否需要主动轮转
     */
    private fun startAutonomousRotationMonitoring() {
        autonomousRotationJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isRunning) {
                try {
                    checkAutonomousRotation()
                    delay(1000) // 每秒检查一次
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 记录错误但继续运行
                }
            }
        }
    }
    
    /**
     * 检查自治状态是否需要轮转
     */
    private suspend fun checkAutonomousRotation() {
        val currentState = stateManager.getCurrentState() as? AutonomousState ?: return
        val currentContext = stateManager.currentContext ?: return
        
        // 检查是否应该主动轮转
        if (!currentState.shouldAutoRotate(currentContext)) {
            return
        }
        
        // 获取自治状态的转移提议
        val proposal = currentState.proposeTransition(currentContext)
        if (proposal != null) {
            // 执行自治状态提议的转移
            executeAutonomousTransition(proposal, currentContext)
        }
    }
    
    /**
     * 执行自治状态提议的转移
     */
    private suspend fun executeAutonomousTransition(
        proposal: StateTransitionProposal,
        context: StateContext
    ) {
        try {
            // 执行转移前检查
            if (!proposal.preTransitionCheck(context)) {
                return
            }
            
            // 更新上下文
            val updatedContext = proposal.contextUpdate(context)
            
            // 执行状态转移
            val result = stateManager.transitionTo(
                proposal.targetStateId,
                proposal.triggerEvent ?: createAutoTransitionEvent(proposal),
                updatedContext
            )
            
            if (result.success) {
                // 执行转移后动作
                proposal.postTransitionAction(result.updatedContext)
            }
        } catch (e: Exception) {
            // 记录错误
        }
    }
    
    /**
     * 创建自动转移事件
     */
    private fun createAutoTransitionEvent(proposal: StateTransitionProposal): Event {
        return EventFactory.builder()
            .type("system", "autonomous_transition")
            .name("自治状态转移")
            .description("由状态${proposal.fromStateId}主动提议转移到${proposal.targetStateId}")
            .source("autonomous-state")
            .payload(mapOf(
                "proposalId" to proposal.proposalId,
                "reason" to proposal.reason.name
            ))
            .build()
    }
    
    override suspend fun registerTransition(transition: StateTransition) {
        transitionRegistry.registerTransition(transition)
    }
    
    override suspend fun unregisterTransition(transitionId: TransitionId) {
        transitionRegistry.unregisterTransition(transitionId)
    }
    
    override fun getStatistics(): OrchestratorStatistics {
        return runBlocking { statistics.getStatistics() }
    }
    
    /**
     * 获取推理引擎
     */
    fun getInferenceEngine(): StateInferenceEngine = stateInferenceEngine
    
    /**
     * 获取上下文推理引擎
     */
    fun getContextReasoningEngine(): ContextReasoningEngine = contextReasoningEngine
    
    /**
     * 获取策略引擎
     */
    fun getPolicyEngine(): PolicyEngine? = policyEngine
    
    private fun createEventListener(): EventListener {
        return object : EventListener {
            override val id: String = "orchestrator-listener"
            
            override suspend fun onEvent(event: Event) {
                processEvent(event)
            }
            
            override suspend fun onError(event: Event, error: Throwable) {
                statistics.recordFailure()
            }
        }
    }
}

/**
 * 状态转移注册表实现
 */
class TransitionRegistryImpl : TransitionRegistry {
    private val transitions = ConcurrentHashMap<TransitionId, StateTransition>()
    private val transitionsBySource = ConcurrentHashMap<StateId, MutableList<StateTransition>>()
    private val transitionsByEventType = ConcurrentHashMap<EventType, MutableList<StateTransition>>()
    
    override suspend fun registerTransition(transition: StateTransition) {
        transitions[transition.id] = transition
        
        transitionsBySource.computeIfAbsent(transition.fromStateId) { mutableListOf() }
            .add(transition)
        
        transitionsByEventType.computeIfAbsent(transition.eventType) { mutableListOf() }
            .add(transition)
    }
    
    override suspend fun unregisterTransition(transitionId: TransitionId) {
        val transition = transitions.remove(transitionId) ?: return
        
        transitionsBySource[transition.fromStateId]?.remove(transition)
        transitionsByEventType[transition.eventType]?.remove(transition)
    }
    
    override fun getTransition(transitionId: TransitionId): StateTransition? {
        return transitions[transitionId]
    }
    
    override fun getAllTransitions(): Collection<StateTransition> {
        return transitions.values
    }
    
    override fun findTransitions(fromStateId: StateId, eventType: EventType): List<StateTransition> {
        val sourceTransitions = transitionsBySource[fromStateId] ?: emptyList()
        val eventTransitions = transitionsByEventType[eventType] ?: emptyList()
        
        return sourceTransitions.intersect(eventTransitions.toSet()).toList()
    }
}

/**
 * 调度器统计信息实现
 */
internal class OrchestratorStatisticsImpl {
    private var totalEventsProcessed = 0L
    private var successfulTransitions = 0L
    private var failedTransitions = 0L
    private var totalProcessingTime = 0L
    private var processingCount = 0L
    private var lastProcessedAt: Instant? = null
    private val mutex = Mutex()
    
    suspend fun recordSuccess() {
        mutex.withLock {
            successfulTransitions++
            lastProcessedAt = Instant.now()
        }
    }
    
    suspend fun recordFailure() {
        mutex.withLock {
            failedTransitions++
            lastProcessedAt = Instant.now()
        }
    }
    
    suspend fun updateProcessingTime(timeMs: Long) {
        mutex.withLock {
            totalEventsProcessed++
            totalProcessingTime += timeMs
            processingCount++
        }
    }
    
    suspend fun getStatistics(): OrchestratorStatistics {
        return mutex.withLock {
            OrchestratorStatistics(
                totalEventsProcessed = totalEventsProcessed,
                successfulTransitions = successfulTransitions,
                failedTransitions = failedTransitions,
                averageProcessingTimeMs = if (processingCount > 0) {
                    totalProcessingTime.toDouble() / processingCount
                } else 0.0,
                lastProcessedAt = lastProcessedAt
            )
        }
    }
}

/**
 * 状态编排器工厂
 */
object StateOrchestratorFactory {
    suspend fun create(
        stateManager: StateManager,
        eventBus: EventBus,
        transitionRegistry: TransitionRegistry = TransitionRegistryImpl(),
        contextReasoningEngine: ContextReasoningEngine? = null,
        policyEngine: PolicyEngine? = null,
        enableAutonomousRotation: Boolean = true,
        inferenceConfig: InferenceConfig = InferenceConfig(),
        reasoningConfig: ReasoningConfig = ReasoningConfig()
    ): StateOrchestrator {
        val stateRegistry = stateManager.stateRegistry
        
        val contextEngine = contextReasoningEngine 
            ?: ContextReasoningEngineFactory.create(stateRegistry, reasoningConfig)
        
        val inferenceEngine = StateInferenceEngineFactory.create(
            stateRegistry = stateRegistry,
            contextReasoningEngine = contextEngine,
            policyEngine = policyEngine,
            config = inferenceConfig
        )
        
        return StateOrchestratorImpl(
            stateManager = stateManager,
            eventBus = eventBus,
            transitionRegistry = transitionRegistry,
            stateInferenceEngine = inferenceEngine,
            contextReasoningEngine = contextEngine,
            policyEngine = policyEngine,
            enableAutonomousRotation = enableAutonomousRotation
        )
    }
}
