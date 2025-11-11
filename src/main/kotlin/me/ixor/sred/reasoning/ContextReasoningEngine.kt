package me.ixor.sred.reasoning

import me.ixor.sred.core.*
import kotlinx.coroutines.*

/**
 * 上下文推理引擎 - 深度利用上下文的四个维度
 * 
 * 符合论文要求："基于上下文驱动的状态迁移"
 * "转移结果不仅依赖于当前状态与事件，还受上下文环境影响"
 * 
 * 上下文 C = (Σ_local, Σ_global, E_recent, M)
 * 
 * 推理引擎能够：
 * 1. 基于上下文的四个维度进行复杂推理
 * 2. 识别上下文模式
 * 3. 预测状态转移的可能性
 * 4. 评估上下文对状态转移的影响
 */
interface ContextReasoningEngine {
    /**
     * 分析上下文，推理可能的状态转移
     * 
     * @param currentState 当前状态
     * @param event 触发事件
     * @param context 状态上下文
     * @param availableStates 可选的状态集合（如果为null，则从注册表查找）
     * @return 推理结果，包含可能的目标状态及其置信度
     */
    suspend fun inferPossibleTransitions(
        currentState: State,
        event: Event,
        context: StateContext,
        availableStates: Collection<State>? = null
    ): List<TransitionInference>
    
    /**
     * 评估上下文对状态转移的支持度
     * 
     * @param fromState 源状态
     * @param toState 目标状态
     * @param event 触发事件
     * @param context 状态上下文
     * @return 支持度分数（0.0-1.0），越高表示上下文越支持此次转移
     */
    suspend fun evaluateContextSupport(
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext
    ): ContextSupportScore
    
    /**
     * 识别上下文模式
     * 
     * 分析上下文中的局部状态、全局状态、事件历史等，识别出特定的模式
     * 例如：高频事件模式、状态序列模式、异常模式等
     * 
     * @param context 状态上下文
     * @return 识别到的上下文模式列表
     */
    suspend fun identifyContextPatterns(context: StateContext): List<ContextPattern>
    
    /**
     * 基于上下文推理最优的转移路径
     * 
     * 考虑上下文的四个维度，推理出从当前状态到目标状态的最优路径
     * 
     * @param currentState 当前状态
     * @param targetStateId 目标状态ID（可选，如果为null则推理最优目标）
     * @param context 状态上下文
     * @return 推理的转移路径
     */
    suspend fun inferOptimalPath(
        currentState: State,
        targetStateId: StateId?,
        context: StateContext
    ): TransitionPath?
    
    /**
     * 分析上下文的健康度
     * 
     * 评估上下文是否完整、一致、有效
     * 
     * @param context 状态上下文
     * @return 上下文健康度报告
     */
    suspend fun analyzeContextHealth(context: StateContext): ContextHealthReport
}

/**
 * 转移推理结果
 */
data class TransitionInference(
    /**
     * 目标状态ID
     */
    val targetStateId: StateId,
    
    /**
     * 目标状态
     */
    val targetState: State,
    
    /**
     * 置信度（0.0-1.0）
     */
    val confidence: Double,
    
    /**
     * 推理依据（为什么推理出这个目标状态）
     */
    val reasoning: TransitionReasoning,
    
    /**
     * 所需的上下文条件
     */
    val requiredContextConditions: Map<String, Any> = emptyMap(),
    
    /**
     * 优先级分数
     */
    val priorityScore: Double = 0.0,
    
    /**
     * 元数据
     */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 推理依据
 */
data class TransitionReasoning(
    /**
     * 主要推理依据（来自哪个上下文维度）
     */
    val primarySource: ContextDimension,
    
    /**
     * 推理说明
     */
    val explanation: String,
    
    /**
     * 支撑证据（来自各个维度的证据）
     */
    val evidence: Map<ContextDimension, Any> = emptyMap(),
    
    /**
     * 推理规则或模式
     */
    val rule: String? = null
)

/**
 * 上下文维度
 */
enum class ContextDimension {
    /**
     * 局部状态 Σ_local
     */
    LOCAL_STATE,
    
    /**
     * 全局状态 Σ_global
     */
    GLOBAL_STATE,
    
    /**
     * 最近事件 E_recent
     */
    RECENT_EVENTS,
    
    /**
     * 元信息 M
     */
    METADATA,
    
    /**
     * 综合多个维度
     */
    COMPOSITE
}

/**
 * 上下文支持度分数
 */
data class ContextSupportScore(
    /**
     * 总体支持度（0.0-1.0）
     */
    val overallScore: Double,
    
    /**
     * 各维度的支持度
     */
    val dimensionScores: Map<ContextDimension, Double> = emptyMap(),
    
    /**
     * 支持的理由
     */
    val supportingReasons: List<String> = emptyList(),
    
    /**
     * 不支持的理由
     */
    val opposingReasons: List<String> = emptyList(),
    
    /**
     * 缺失的关键上下文信息
     */
    val missingContext: List<String> = emptyList()
)

/**
 * 上下文模式
 */
data class ContextPattern(
    /**
     * 模式类型
     */
    val type: PatternType,
    
    /**
     * 模式名称
     */
    val name: String,
    
    /**
     * 模式匹配度（0.0-1.0）
     */
    val matchScore: Double,
    
    /**
     * 模式特征
     */
    val features: Map<String, Any> = emptyMap(),
    
    /**
     * 模式预测的行为或状态
     */
    val prediction: String? = null,
    
    /**
     * 元数据
     */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 模式类型
 */
enum class PatternType {
    /**
     * 事件频率模式：高频/低频事件
     */
    EVENT_FREQUENCY,
    
    /**
     * 状态序列模式：特定的状态转移序列
     */
    STATE_SEQUENCE,
    
    /**
     * 上下文变化模式：上下文值的变化趋势
     */
    CONTEXT_TREND,
    
    /**
     * 异常模式：异常的上下文组合
     */
    ANOMALY,
    
    /**
     * 周期性模式：时间周期性的模式
     */
    PERIODIC,
    
    /**
     * 相关性模式：上下文维度之间的相关性
     */
    CORRELATION
}

/**
 * 转移路径
 */
data class TransitionPath(
    /**
     * 路径步骤列表
     */
    val steps: List<PathStep>,
    
    /**
     * 路径总代价（数值越小越好）
     */
    val totalCost: Double,
    
    /**
     * 路径置信度
     */
    val confidence: Double,
    
    /**
     * 路径描述
     */
    val description: String = ""
)

/**
 * 路径步骤
 */
data class PathStep(
    /**
     * 源状态ID
     */
    val fromStateId: StateId,
    
    /**
     * 目标状态ID
     */
    val toStateId: StateId,
    
    /**
     * 触发事件类型（可选）
     */
    val eventType: EventType?,
    
    /**
     * 步骤代价
     */
    val cost: Double,
    
    /**
     * 所需上下文条件
     */
    val requiredContext: Map<String, Any> = emptyMap()
)

/**
 * 上下文健康度报告
 */
data class ContextHealthReport(
    /**
     * 总体健康度（0.0-1.0）
     */
    val overallHealth: Double,
    
    /**
     * 各维度的健康度
     */
    val dimensionHealth: Map<ContextDimension, Double> = emptyMap(),
    
    /**
     * 健康度问题列表
     */
    val issues: List<HealthIssue> = emptyList(),
    
    /**
     * 建议
     */
    val suggestions: List<String> = emptyList()
)

/**
 * 健康度问题
 */
data class HealthIssue(
    /**
     * 问题类型
     */
    val type: IssueType,
    
    /**
     * 问题严重程度（0.0-1.0，1.0最严重）
     */
    val severity: Double,
    
    /**
     * 问题描述
     */
    val description: String,
    
    /**
     * 受影响的维度
     */
    val affectedDimension: ContextDimension? = null
)

/**
 * 问题类型
 */
enum class IssueType {
    /**
     * 缺失关键信息
     */
    MISSING_DATA,
    
    /**
     * 数据不一致
     */
    INCONSISTENT_DATA,
    
    /**
     * 数据过期
     */
    STALE_DATA,
    
    /**
     * 数据冲突
     */
    CONFLICTING_DATA,
    
    /**
     * 模式异常
     */
    ANOMALOUS_PATTERN
}

/**
 * 上下文推理引擎实现
 */
class ContextReasoningEngineImpl(
    private val stateRegistry: me.ixor.sred.state.StateRegistry,
    private val config: ReasoningConfig = ReasoningConfig()
) : ContextReasoningEngine {
    
    override suspend fun inferPossibleTransitions(
        currentState: State,
        event: Event,
        context: StateContext,
        availableStates: Collection<State>?
    ): List<TransitionInference> = withContext(Dispatchers.Default) {
        val states = availableStates ?: stateRegistry.getAllStates()
        val inferences = mutableListOf<TransitionInference>()
        
        for (state in states) {
            if (state.id == currentState.id) continue
            
            val supportScore = evaluateContextSupport(currentState, state, event, context)
            
            if (supportScore.overallScore > config.minConfidenceThreshold) {
                val reasoning = analyzeTransitionReasoning(
                    currentState,
                    state,
                    event,
                    context,
                    supportScore
                )
                
                inferences.add(
                    TransitionInference(
                        targetStateId = state.id,
                        targetState = state,
                        confidence = supportScore.overallScore,
                        reasoning = reasoning,
                        requiredContextConditions = extractRequiredConditions(state, context),
                        priorityScore = calculatePriorityScore(state, context, supportScore)
                    )
                )
            }
        }
        
        // 按置信度和优先级排序
        inferences.sortedByDescending { it.confidence * 0.7 + it.priorityScore * 0.3 }
    }
    
    override suspend fun evaluateContextSupport(
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext
    ): ContextSupportScore = withContext(Dispatchers.Default) {
        val dimensionScores = mutableMapOf<ContextDimension, Double>()
        val supportingReasons = mutableListOf<String>()
        val opposingReasons = mutableListOf<String>()
        val missingContext = mutableListOf<String>()
        
        // 评估局部状态维度
        val localScore = evaluateLocalStateSupport(fromState, toState, event, context)
        dimensionScores[ContextDimension.LOCAL_STATE] = localScore
        if (localScore > 0.7) {
            supportingReasons.add("局部状态条件满足")
        } else if (localScore < 0.3) {
            opposingReasons.add("局部状态条件不满足")
        }
        
        // 评估全局状态维度
        val globalScore = evaluateGlobalStateSupport(fromState, toState, event, context)
        dimensionScores[ContextDimension.GLOBAL_STATE] = globalScore
        if (globalScore > 0.7) {
            supportingReasons.add("全局状态条件满足")
        }
        
        // 评估事件历史维度
        val eventScore = evaluateEventHistorySupport(fromState, toState, event, context)
        dimensionScores[ContextDimension.RECENT_EVENTS] = eventScore
        if (eventScore > 0.5) {
            supportingReasons.add("事件历史支持此次转移")
        }
        
        // 评估元信息维度
        val metadataScore = evaluateMetadataSupport(fromState, toState, event, context)
        dimensionScores[ContextDimension.METADATA] = metadataScore
        
        // 计算总体支持度（加权平均）
        val overallScore = (
            localScore * config.localStateWeight +
            globalScore * config.globalStateWeight +
            eventScore * config.eventHistoryWeight +
            metadataScore * config.metadataWeight
        ) / (config.localStateWeight + config.globalStateWeight + 
             config.eventHistoryWeight + config.metadataWeight)
        
        ContextSupportScore(
            overallScore = overallScore,
            dimensionScores = dimensionScores,
            supportingReasons = supportingReasons,
            opposingReasons = opposingReasons,
            missingContext = missingContext
        )
    }
    
    override suspend fun identifyContextPatterns(context: StateContext): List<ContextPattern> {
        val patterns = mutableListOf<ContextPattern>()
        
        // 识别事件频率模式
        patterns.addAll(identifyEventFrequencyPatterns(context))
        
        // 识别上下文趋势模式
        patterns.addAll(identifyContextTrendPatterns(context))
        
        // 识别异常模式
        patterns.addAll(identifyAnomalyPatterns(context))
        
        return patterns
    }
    
    override suspend fun inferOptimalPath(
        currentState: State,
        targetStateId: StateId?,
        context: StateContext
    ): TransitionPath? {
        // TODO: 实现路径查找算法（如A*、Dijkstra等）
        // 考虑上下文的各个维度来计算路径代价
        return null
    }
    
    override suspend fun analyzeContextHealth(context: StateContext): ContextHealthReport {
        val dimensionHealth = mutableMapOf<ContextDimension, Double>()
        val issues = mutableListOf<HealthIssue>()
        
        // 分析各维度健康度
        dimensionHealth[ContextDimension.LOCAL_STATE] = analyzeLocalStateHealth(context)
        dimensionHealth[ContextDimension.GLOBAL_STATE] = analyzeGlobalStateHealth(context)
        dimensionHealth[ContextDimension.RECENT_EVENTS] = analyzeEventHistoryHealth(context)
        dimensionHealth[ContextDimension.METADATA] = analyzeMetadataHealth(context)
        
        // 计算总体健康度
        val overallHealth = dimensionHealth.values.average()
        
        return ContextHealthReport(
            overallHealth = overallHealth,
            dimensionHealth = dimensionHealth,
            issues = issues,
            suggestions = generateHealthSuggestions(dimensionHealth, issues)
        )
    }
    
    // ========== 私有辅助方法 ==========
    
    private suspend fun evaluateLocalStateSupport(
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext
    ): Double {
        // 检查目标状态是否可以进入（使用canEnter）
        return if (toState.canEnter(context)) {
            // 进一步分析局部状态是否满足转移条件
            0.8 // 简化实现，实际应该更复杂
        } else {
            0.2
        }
    }
    
    private suspend fun evaluateGlobalStateSupport(
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext
    ): Double {
        // 分析全局状态是否支持转移
        // 例如：系统负载、全局配置等
        return 0.7 // 简化实现
    }
    
    private suspend fun evaluateEventHistorySupport(
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext
    ): Double {
        // 分析事件历史是否支持此次转移
        // 例如：是否存在前置事件、事件序列是否合理等
        val recentEvents = context.recentEvents
        return if (recentEvents.isNotEmpty()) {
            // 检查事件序列的合理性
            0.6
        } else {
            0.5 // 中性
        }
    }
    
    private suspend fun evaluateMetadataSupport(
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext
    ): Double {
        // 分析元信息是否支持转移
        return 0.7
    }
    
    private fun analyzeTransitionReasoning(
        fromState: State,
        toState: State,
        event: Event,
        context: StateContext,
        supportScore: ContextSupportScore
    ): TransitionReasoning {
        // 找出最主要的支持维度
        val primarySource = supportScore.dimensionScores.maxByOrNull { it.value }?.key 
            ?: ContextDimension.COMPOSITE
        
        val explanation = when (primarySource) {
            ContextDimension.LOCAL_STATE -> "局部状态条件满足，支持转移到${toState.name}"
            ContextDimension.GLOBAL_STATE -> "全局状态支持，系统条件允许转移到${toState.name}"
            ContextDimension.RECENT_EVENTS -> "事件历史模式支持转移到${toState.name}"
            ContextDimension.METADATA -> "元信息指示应该转移到${toState.name}"
            else -> "综合分析支持转移到${toState.name}"
        }
        
        return TransitionReasoning(
            primarySource = primarySource,
            explanation = explanation,
            evidence = supportScore.dimensionScores.mapKeys { it.key }
        )
    }
    
    private fun extractRequiredConditions(
        state: State,
        context: StateContext
    ): Map<String, Any> {
        // 提取进入状态所需的条件
        return emptyMap() // 简化实现
    }
    
    private fun calculatePriorityScore(
        state: State,
        context: StateContext,
        supportScore: ContextSupportScore
    ): Double {
        // 计算优先级分数
        return supportScore.overallScore * 0.8 + 0.2 // 简化实现
    }
    
    private suspend fun identifyEventFrequencyPatterns(context: StateContext): List<ContextPattern> {
        val events = context.recentEvents
        if (events.isEmpty()) return emptyList()
        
        // 统计事件频率
        val eventCounts = events.groupingBy { it.type }.eachCount()
        val maxCount = eventCounts.values.maxOrNull() ?: 0
        val totalEvents = events.size
        
        return if (maxCount > totalEvents * 0.5) {
            // 发现高频事件模式
            listOf(
                ContextPattern(
                    type = PatternType.EVENT_FREQUENCY,
                    name = "高频事件模式",
                    matchScore = maxCount.toDouble() / totalEvents,
                    features = mapOf(
                        "maxFrequency" to maxCount,
                        "totalEvents" to totalEvents
                    )
                )
            )
        } else {
            emptyList()
        }
    }
    
    private suspend fun identifyContextTrendPatterns(context: StateContext): List<ContextPattern> {
        // TODO: 实现趋势模式识别
        return emptyList()
    }
    
    private suspend fun identifyAnomalyPatterns(context: StateContext): List<ContextPattern> {
        // TODO: 实现异常模式识别
        return emptyList()
    }
    
    private fun analyzeLocalStateHealth(context: StateContext): Double {
        // 检查局部状态的完整性、一致性
        return if (context.localState.isNotEmpty()) 0.8 else 0.5
    }
    
    private fun analyzeGlobalStateHealth(context: StateContext): Double {
        return 0.7
    }
    
    private fun analyzeEventHistoryHealth(context: StateContext): Double {
        return if (context.recentEvents.size in 1..100) 0.8 else 0.6
    }
    
    private fun analyzeMetadataHealth(context: StateContext): Double {
        return 0.7
    }
    
    private fun generateHealthSuggestions(
        dimensionHealth: Map<ContextDimension, Double>,
        issues: List<HealthIssue>
    ): List<String> {
        val suggestions = mutableListOf<String>()
        
        dimensionHealth.forEach { (dimension, health) ->
            if (health < 0.6) {
                suggestions.add("${dimension.name}的健康度较低，建议检查相关数据")
            }
        }
        
        return suggestions
    }
}

/**
 * 推理配置
 */
data class ReasoningConfig(
    /**
     * 最小置信度阈值
     */
    val minConfidenceThreshold: Double = 0.5,
    
    /**
     * 各维度权重
     */
    val localStateWeight: Double = 0.4,
    val globalStateWeight: Double = 0.3,
    val eventHistoryWeight: Double = 0.2,
    val metadataWeight: Double = 0.1
)

/**
 * 推理引擎工厂
 */
object ContextReasoningEngineFactory {
    fun create(
        stateRegistry: me.ixor.sred.state.StateRegistry,
        config: ReasoningConfig = ReasoningConfig()
    ): ContextReasoningEngine = ContextReasoningEngineImpl(stateRegistry, config)
}




