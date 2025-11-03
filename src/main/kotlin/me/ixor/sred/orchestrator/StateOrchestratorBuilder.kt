package me.ixor.sred.orchestrator

import me.ixor.sred.core.*
import me.ixor.sred.event.*
import me.ixor.sred.state.*
import me.ixor.sred.reasoning.*
import me.ixor.sred.policy.*
import me.ixor.sred.persistence.ExtendedStatePersistence
import kotlinx.coroutines.*
import kotlinx.coroutines.runBlocking

/**
 * 状态编排器构建器 - 构建整合动态推理、自治状态和策略引擎的智能编排器
 */
class StateOrchestratorBuilder {
    private var stateManager: StateManager? = null
    private var eventBus: EventBus? = null
    private var transitionRegistry: TransitionRegistry = TransitionRegistryImpl()
    private var contextReasoningEngine: ContextReasoningEngine? = null
    private var policyEngine: PolicyEngine? = null
    private var enableAutonomousRotation: Boolean = true
    private var inferenceConfig: InferenceConfig = InferenceConfig()
    private var reasoningConfig: ReasoningConfig = ReasoningConfig()
    private var persistence: ExtendedStatePersistence? = null
    
    /**
     * 设置状态管理器
     */
    fun withStateManager(manager: StateManager) = apply {
        this.stateManager = manager
    }
    
    /**
     * 设置事件总线
     */
    fun withEventBus(bus: EventBus) = apply {
        this.eventBus = bus
    }
    
    /**
     * 设置转移注册表
     */
    fun withTransitionRegistry(registry: TransitionRegistry) = apply {
        this.transitionRegistry = registry
    }
    
    /**
     * 设置上下文推理引擎
     */
    fun withContextReasoningEngine(engine: ContextReasoningEngine) = apply {
        this.contextReasoningEngine = engine
    }
    
    /**
     * 设置策略引擎
     */
    fun withPolicyEngine(engine: PolicyEngine) = apply {
        this.policyEngine = engine
    }
    
    /**
     * 启用/禁用自治轮转
     */
    fun enableAutonomousRotation(enabled: Boolean) = apply {
        this.enableAutonomousRotation = enabled
    }
    
    /**
     * 设置推理配置
     */
    fun withInferenceConfig(config: InferenceConfig) = apply {
        this.inferenceConfig = config
    }
    
    /**
     * 设置推理配置
     */
    fun withReasoningConfig(config: ReasoningConfig) = apply {
        this.reasoningConfig = config
    }
    
    /**
     * 设置持久化适配器（用于EnhancedStateManager）
     */
    fun withExtendedStatePersistence(persistence: ExtendedStatePersistence) = apply {
        this.persistence = persistence
    }
    
    /**
     * 构建编排器
     */
    suspend fun build(): StateOrchestrator {
        // 如果没有提供 StateManager，创建一个默认的
        val persistenceValue = this.persistence
        val manager = stateManager ?: runBlocking {
            val registry = StateRegistryFactory.create()
            val statePersistence = if (persistenceValue != null) {
                persistenceValue as me.ixor.sred.state.StatePersistence
            } else {
                InMemoryStatePersistence()
            }
            StateManagerFactory.create(registry, statePersistence).also {
                it.initialize()
            }
        }
        
        // 如果没有提供 EventBus，创建一个默认的
        val bus = eventBus ?: EventBusFactory.create()
        
        return StateOrchestratorFactory.create(
            stateManager = manager,
            eventBus = bus,
            transitionRegistry = transitionRegistry,
            contextReasoningEngine = contextReasoningEngine,
            policyEngine = policyEngine,
            enableAutonomousRotation = enableAutonomousRotation,
            inferenceConfig = inferenceConfig,
            reasoningConfig = reasoningConfig
        )
    }
    
    /**
     * 构建并启动编排器
     */
    suspend fun buildAndStart(): StateOrchestrator {
        val orchestrator = build()
        orchestrator.start()
        return orchestrator
    }
    
    companion object {
        fun create(): StateOrchestratorBuilder = StateOrchestratorBuilder()
    }
}
