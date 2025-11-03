package me.ixor.sred.reasoning

import me.ixor.sred.core.*
import me.ixor.sred.state.StateRegistry
import me.ixor.sred.policy.*
import kotlinx.coroutines.*

/**
 * 动态状态推理引擎 - 运行时动态推理状态转移路径
 * 
 * 符合论文要求："执行路径在运行时动态生成，而非编译时确定"
 * "系统应自行知道何时、为何、如何行动"
 * 
 * 推理引擎能够：
 * 1. 在运行时动态发现可能的转移路径（而非依赖预定义）
 * 2. 基于上下文推理最优的转移方案
 * 3. 考虑多因素进行综合决策
 */
interface StateInferenceEngine {
    /**
     * 动态推理可能的状态转移
     * 
     * 不依赖预定义的TransitionRegistry，而是基于：
     * - 当前状态的getPossibleTransitions方法
     * - 自治状态的主动提议
     * - 上下文推理引擎的推理结果
     * - 策略引擎的策略决策
     * 
     * @param currentState 当前状态
     * @param event 触发事件
     * @param context 状态上下文
     * @param predefinedTransitions 预定义的转移（可选，用于兼容性）
     * @return 推理出的可能转移列表（按优先级排序）
     */
    suspend fun inferTransitions(
        currentState: State,
        event: Event,
        context: StateContext,
        predefinedTransitions: List<StateTransition> = emptyList()
    ): List<InferredTransition>
    
    /**
     * 综合评估并选择最优转移
     * 
     * 综合考虑多个来源的转移建议，选择最优方案：
     * - 预定义转移（兼容性）
     * - 自治状态提议（自主性）
     * - 上下文推理结果（智能性）
     * - 策略引擎决策（策略性）
     * 
     * @param currentState 当前状态
     * @param event 触发事件
     * @param context 状态上下文
     * @param predefinedTransitions 预定义转移
     * @return 最优转移方案
     */
    suspend fun selectOptimalTransition(
        currentState: State,
        event: Event,
        context: StateContext,
        predefinedTransitions: List<StateTransition> = emptyList()
    ): OptimalTransition?
}

/**
 * 推理出的状态转移
 */
data class InferredTransition(
    /**
     * 目标状态ID
     */
    val targetStateId: StateId,
    
    /**
     * 目标状态
     */
    val targetState: State,
    
    /**
     * 推理来源
     */
    val source: InferenceSource,
    
    /**
     * 置信度（0.0-1.0）
     */
    val confidence: Double,
    
    /**
     * 优先级分数（综合多个因素）
     */
    val priorityScore: Double,
    
    /**
     * 推理依据
     */
    val reasoning: String,
    
    /**
     * 所需的上下文条件
     */
    val requiredContext: Map<String, Any> = emptyMap(),
    
    /**
     * 如果是预定义转移，保留原转移对象
     */
    val originalTransition: StateTransition? = null,
    
    /**
     * 如果是自治状态提议，保留提议对象
     */
    val autonomousProposal: StateTransitionProposal? = null,
    
    /**
     * 元数据
     */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 推理来源
 */
enum class InferenceSource {
    /**
     * 预定义转移（来自TransitionRegistry）
     */
    PREDEFINED,
    
    /**
     * 状态主动提议（来自getPossibleTransitions）
     */
    STATE_PROPOSAL,
    
    /**
     * 自治状态主动提议
     */
    AUTONOMOUS_STATE,
    
    /**
     * 上下文推理引擎推理
     */
    CONTEXT_REASONING,
    
    /**
     * 策略引擎决策
     */
    POLICY_DECISION,
    
    /**
     * 综合多源
     */
    COMPOSITE
}

/**
 * 最优转移方案
 */
data class OptimalTransition(
    /**
     * 推理出的转移
     */
    val inferredTransition: InferredTransition,
    
    /**
     * 选择理由
     */
    val selectionReason: String,
    
    /**
     * 综合评分
     */
    val compositeScore: Double,
    
    /**
     * 各维度评分
     */
    val dimensionScores: Map<EvaluationDimension, Double> = emptyMap(),
    
    /**
     * 备选方案（按评分排序）
     */
    val alternatives: List<InferredTransition> = emptyList()
)

/**
 * 评估维度
 */
enum class EvaluationDimension {
    /**
     * 置信度
     */
    CONFIDENCE,
    
    /**
     * 优先级
     */
    PRIORITY,
    
    /**
     * 上下文支持度
     */
    CONTEXT_SUPPORT,
    
    /**
     * 策略符合度
     */
    POLICY_COMPLIANCE,
    
    /**
     * 执行效率
     */
    EFFICIENCY,
    
    /**
     * 风险度
     */
    RISK
}

/**
 * 动态状态推理引擎实现
 */
class StateInferenceEngineImpl(
    private val stateRegistry: StateRegistry,
    private val contextReasoningEngine: ContextReasoningEngine,
    private val policyEngine: PolicyEngine? = null,
    private val config: InferenceConfig = InferenceConfig()
) : StateInferenceEngine {
    
    override suspend fun inferTransitions(
        currentState: State,
        event: Event,
        context: StateContext,
        predefinedTransitions: List<StateTransition>
    ): List<InferredTransition> = withContext(Dispatchers.Default) {
        val inferredTransitions = mutableListOf<InferredTransition>()
        
        // 1. 收集预定义转移
        predefinedTransitions.forEach { transition ->
            if (transition.canTransition(currentState, event, context)) {
                inferredTransitions.add(
                    InferredTransition(
                        targetStateId = transition.toStateId,
                        targetState = stateRegistry.getState(transition.toStateId) ?: return@forEach,
                        source = InferenceSource.PREDEFINED,
                        confidence = 0.8, // 预定义转移的置信度较高
                        priorityScore = transition.priority.toDouble(),
                        reasoning = "预定义转移：${transition.name}",
                        originalTransition = transition
                    )
                )
            }
        }
        
        // 2. 收集状态主动提议的转移
        val stateProposedTransitions = currentState.getPossibleTransitions(context)
        stateProposedTransitions.forEach { targetStateId ->
            val targetState = stateRegistry.getState(targetStateId) ?: return@forEach
            inferredTransitions.add(
                InferredTransition(
                    targetStateId = targetStateId,
                    targetState = targetState,
                    source = InferenceSource.STATE_PROPOSAL,
                    confidence = 0.7,
                    priorityScore = 0.5,
                    reasoning = "状态${currentState.name}主动提议转移到${targetState.name}"
                )
            )
        }
        
        // 3. 如果是自治状态，收集主动提议
        if (currentState is AutonomousState) {
            val autonomousProposals = currentState.checkRotationPossibility(context)
            autonomousProposals.forEach { proposal ->
                val targetState = stateRegistry.getState(proposal.targetStateId) ?: return@forEach
                inferredTransitions.add(
                    InferredTransition(
                        targetStateId = proposal.targetStateId,
                        targetState = targetState,
                        source = InferenceSource.AUTONOMOUS_STATE,
                        confidence = proposal.confidence,
                        priorityScore = proposal.priority.toDouble(),
                        reasoning = "自治状态主动提议：${proposal.reason} - ${proposal.justification}",
                        autonomousProposal = StateTransitionProposal(
                            proposalId = "${currentState.id}_${proposal.targetStateId}_${System.currentTimeMillis()}",
                            fromStateId = currentState.id,
                            targetStateId = proposal.targetStateId,
                            triggerEvent = event,
                            reason = proposal.reason,
                            confidence = proposal.confidence,
                            priority = proposal.priority,
                            contextUpdate = { ctx -> 
                                ctx.copy(localState = ctx.localState + proposal.requiredContextChanges)
                            }
                        ),
                        requiredContext = proposal.requiredContextChanges
                    )
                )
            }
        }
        
        // 4. 使用上下文推理引擎推理
        val contextInferences = contextReasoningEngine.inferPossibleTransitions(
            currentState,
            event,
            context
        )
        contextInferences.forEach { inference ->
            inferredTransitions.add(
                InferredTransition(
                    targetStateId = inference.targetStateId,
                    targetState = inference.targetState,
                    source = InferenceSource.CONTEXT_REASONING,
                    confidence = inference.confidence,
                    priorityScore = inference.priorityScore,
                    reasoning = "上下文推理：${inference.reasoning.explanation}",
                    requiredContext = inference.requiredContextConditions,
                    metadata = mapOf(
                        "primarySource" to inference.reasoning.primarySource.name,
                        "evidence" to inference.reasoning.evidence
                    )
                )
            )
        }
        
        // 5. 使用策略引擎决策（如果有）
        policyEngine?.let { engine: PolicyEngine ->
            val candidateStates = inferredTransitions.map { it.targetState }.toList()
            val policyTransitions = engine.evaluateTransitions(
                currentState,
                event,
                context,
                candidateStates
            )
            policyTransitions.forEach { policyDecision: PolicyDecision ->
                inferredTransitions.add(
                    InferredTransition(
                        targetStateId = policyDecision.targetStateId,
                        targetState = policyDecision.targetState,
                        source = InferenceSource.POLICY_DECISION,
                        confidence = policyDecision.confidence,
                        priorityScore = policyDecision.priorityScore,
                        reasoning = "策略决策：${policyDecision.reasoning}",
                        metadata = mapOf("policyId" to policyDecision.policyId)
                    )
                )
            }
        }
        
        // 去重并合并（相同目标状态可能来自多个来源）
        val mergedTransitions = mergeInferredTransitions(inferredTransitions)
        
        // 按优先级和置信度排序
        mergedTransitions.sortedByDescending { 
            it.confidence * config.confidenceWeight + 
            it.priorityScore * config.priorityWeight 
        }
    }
    
    override suspend fun selectOptimalTransition(
        currentState: State,
        event: Event,
        context: StateContext,
        predefinedTransitions: List<StateTransition>
    ): OptimalTransition? = withContext(Dispatchers.Default) {
        // 1. 推理所有可能的转移
        val allTransitions = inferTransitions(currentState, event, context, predefinedTransitions)
        
        if (allTransitions.isEmpty()) {
            return@withContext null
        }
        
        // 2. 综合评估每个转移
        val evaluatedTransitions = allTransitions.map { transition ->
            evaluateTransition(currentState, transition, event, context)
        }
        
        // 3. 选择最优转移
        val optimal = evaluatedTransitions.maxByOrNull { it.compositeScore }
            ?: return@withContext null
        
        // 4. 准备备选方案
        val alternatives = evaluatedTransitions
            .sortedByDescending { it.compositeScore }
            .drop(1) // 排除最优的
            .take(3) // 保留前3个备选
            .map { it.inferredTransition }
        
        OptimalTransition(
            inferredTransition = optimal.inferredTransition,
            selectionReason = generateSelectionReason(optimal),
            compositeScore = optimal.compositeScore,
            dimensionScores = optimal.dimensionScores,
            alternatives = alternatives
        )
    }
    
    // ========== 私有辅助方法 ==========
    
    /**
     * 合并推理出的转移（相同目标状态合并）
     */
    private fun mergeInferredTransitions(
        transitions: List<InferredTransition>
    ): List<InferredTransition> {
        val grouped = transitions.groupBy { it.targetStateId }
        
        return grouped.map { (_, group) ->
            if (group.size == 1) {
                group.first()
            } else {
                // 合并多个来源的推理结果
                val maxConfidence = group.maxOf { it.confidence }
                val maxPriority = group.maxOf { it.priorityScore }
                val combinedReasoning = group.joinToString("; ") { it.reasoning }
                val combinedSources = group.map { it.source }.distinct()
                
                // 创建合并后的转移
                val primary = group.maxByOrNull { 
                    it.confidence * config.confidenceWeight + it.priorityScore * config.priorityWeight
                } ?: group.first()
                
                primary.copy(
                    source = if (combinedSources.size > 1) InferenceSource.COMPOSITE else primary.source,
                    confidence = maxConfidence,
                    priorityScore = maxPriority,
                    reasoning = "综合推理：$combinedReasoning",
                    metadata = primary.metadata + mapOf(
                        "sourceCount" to group.size,
                        "sources" to combinedSources.map { it.name }
                    )
                )
            }
        }
    }
    
    /**
     * 评估转移的综合得分
     */
    private suspend fun evaluateTransition(
        currentState: State,
        transition: InferredTransition,
        event: Event,
        context: StateContext
    ): OptimalTransition {
        val dimensionScores = mutableMapOf<EvaluationDimension, Double>()
        
        // 置信度维度
        dimensionScores[EvaluationDimension.CONFIDENCE] = transition.confidence
        
        // 优先级维度
        dimensionScores[EvaluationDimension.PRIORITY] = transition.priorityScore / 10.0 // 归一化
        
        // 上下文支持度
        val contextSupport = contextReasoningEngine.evaluateContextSupport(
            currentState,
            transition.targetState,
            event,
            context
        )
        dimensionScores[EvaluationDimension.CONTEXT_SUPPORT] = contextSupport.overallScore
        
        // 策略符合度（如果有策略引擎）
        val policyScore = if (policyEngine != null) {
            policyEngine.evaluateTransitionCompliance(
                currentState,
                transition.targetState,
                event,
                context
            )
        } else {
            0.5 // 中性
        }
        dimensionScores[EvaluationDimension.POLICY_COMPLIANCE] = policyScore
        
        // 执行效率（简化评估）
        dimensionScores[EvaluationDimension.EFFICIENCY] = 0.7
        
        // 风险度（简化评估）
        dimensionScores[EvaluationDimension.RISK] = 0.3 // 较低风险
        
        // 计算综合得分
        val compositeScore = (
            dimensionScores[EvaluationDimension.CONFIDENCE]!! * 0.3 +
            dimensionScores[EvaluationDimension.PRIORITY]!! * 0.2 +
            dimensionScores[EvaluationDimension.CONTEXT_SUPPORT]!! * 0.25 +
            dimensionScores[EvaluationDimension.POLICY_COMPLIANCE]!! * 0.15 +
            dimensionScores[EvaluationDimension.EFFICIENCY]!! * 0.05 +
            (1.0 - dimensionScores[EvaluationDimension.RISK]!!) * 0.05 // 风险越低越好
        )
        
        return OptimalTransition(
            inferredTransition = transition,
            selectionReason = "",
            compositeScore = compositeScore,
            dimensionScores = dimensionScores
        )
    }
    
    private fun generateSelectionReason(optimal: OptimalTransition): String {
        val topDimensions = optimal.dimensionScores
            .entries
            .sortedByDescending { (_, value) -> value }
            .take(2)
            .map { (key, value) -> "${key.name}: ${String.format("%.2f", value)}" }
            .joinToString(", ")
        
        return "综合评分: ${String.format("%.2f", optimal.compositeScore)}, " +
               "主要优势: $topDimensions, " +
               "来源: ${optimal.inferredTransition.source.name}"
    }
}

/**
 * 推理配置
 */
data class InferenceConfig(
    /**
     * 置信度权重
     */
    val confidenceWeight: Double = 0.4,
    
    /**
     * 优先级权重
     */
    val priorityWeight: Double = 0.3,
    
    /**
     * 上下文支持度权重
     */
    val contextSupportWeight: Double = 0.2,
    
    /**
     * 策略符合度权重
     */
    val policyComplianceWeight: Double = 0.1
)

/**
 * 推理引擎工厂
 */
object StateInferenceEngineFactory {
    fun create(
        stateRegistry: StateRegistry,
        contextReasoningEngine: ContextReasoningEngine,
        policyEngine: PolicyEngine? = null,
        config: InferenceConfig = InferenceConfig()
    ): StateInferenceEngine = StateInferenceEngineImpl(
        stateRegistry,
        contextReasoningEngine,
        policyEngine,
        config
    )
}

