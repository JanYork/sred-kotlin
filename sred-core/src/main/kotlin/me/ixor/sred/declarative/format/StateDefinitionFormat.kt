package me.ixor.sred.declarative.format

import me.ixor.sred.core.*
import me.ixor.sred.declarative.*
import com.fasterxml.jackson.module.kotlin.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

/**
 * 状态定义格式接口
 */
interface StateDefinitionFormat {
    fun parse(content: String): StateDefinition
    fun serialize(definition: StateDefinition): String
}

/**
 * 状态定义数据类
 */
data class StateDefinition(
    val name: String,
    val description: String = "",
    val version: String = "1.0.0",
    val author: String = "",
    val states: List<StateInfo> = emptyList(),
    val transitions: List<TransitionInfo> = emptyList(),
    val functions: List<FunctionInfo> = emptyList(),
    val metadata: Map<String, Any> = emptyMap(),
    // 流程级别配置
    val config: WorkflowConfig? = null
)

/**
 * 工作流配置
 */
data class WorkflowConfig(
    // 是否支持长时间停顿（流程级别默认值）
    val pauseable: Boolean = false,
    // 默认超时时间（秒），null 表示不超时
    val defaultTimeout: Long? = null,
    // 是否自动恢复暂停的流程
    val autoResume: Boolean = false
)

/**
 * 超时操作配置
 */
data class TimeoutAction(
    val type: String,  // "transition" 或 "event"
    val targetState: String? = null,  // type="transition" 时使用
    val eventType: String? = null,    // type="event" 时使用
    val eventName: String? = null     // type="event" 时使用
)

/**
 * 状态信息
 */
data class StateInfo(
    val id: String,
    val name: String,
    val type: StateType,
    val parentId: String? = null,
    val isInitial: Boolean = false,
    val isFinal: Boolean = false,
    val isError: Boolean = false,
    val description: String = "",
    val metadata: Map<String, Any> = emptyMap(),
    // 状态级别配置
    val pauseable: Boolean? = null,  // null 表示继承流程配置
    val timeout: Long? = null,       // 超时时间（秒），null 表示不超时，-1 表示无限久
    val pauseOnEnter: Boolean = false,  // 进入此状态时自动暂停
    val timeoutAction: TimeoutAction? = null,  // 超时后的操作
    // 执行模式配置
    val executionMode: String? = null,  // "SEQUENTIAL", "PARALLEL", "CONDITIONAL", "JOIN"
    val branchConfig: List<BranchConfigInfo>? = null,  // 分支配置（CONDITIONAL模式）
    val parallelConfig: ParallelConfigInfo? = null  // 并行配置（PARALLEL模式）
)

/**
 * 分支配置信息（JSON格式）
 */
data class BranchConfigInfo(
    val name: String,
    val targetStateId: String,
    val condition: ConditionInfo,  // 条件信息
    val priority: Int = 0,
    val description: String = "",
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 条件信息（JSON格式）
 */
data class ConditionInfo(
    val type: String,  // "LOCAL_STATE", "GLOBAL_STATE", "EXPRESSION", "COMPOSITE"
    val key: String? = null,  // 用于LOCAL_STATE/GLOBAL_STATE类型
    val operator: String? = null,  // "EQ", "NE", "GT", "GE", "LT", "LE", "IN", "CONTAINS"
    val value: Any? = null,  // 比较值
    val expression: String? = null,  // 用于EXPRESSION类型
    val expressionType: String? = null,  // "JSON_PATH", "SIMPLE", "GROOVY"
    val logicalOperator: String? = null,  // "AND", "OR", "NOT" 用于COMPOSITE类型
    val conditions: List<ConditionInfo>? = null  // 用于COMPOSITE类型
)

/**
 * 并行配置信息（JSON格式）
 */
data class ParallelConfigInfo(
    val branches: List<ParallelBranchInfo>,
    val waitStrategy: String = "ALL",  // "ALL", "ANY", "N_COUNT"
    val timeout: Long? = null,
    val errorStrategy: String = "FAIL_ALL"  // "FAIL_ALL", "IGNORE_FAILURES", "TOLERATE_FAILURES"
)

/**
 * 并行分支信息（JSON格式）
 */
data class ParallelBranchInfo(
    val branchId: String,
    val targetStateId: String,
    val description: String = "",
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * TransitionCondition自定义反序列化器
 */
class TransitionConditionDeserializer : JsonDeserializer<TransitionCondition>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TransitionCondition {
        val value = p.text
        return when (value) {
            "Success", "SUCCESS" -> TransitionCondition.Success
            "Failure", "FAILURE" -> TransitionCondition.Failure
            else -> TransitionCondition.Custom(value)
        }
    }
}

/**
 * 转移信息
 */
data class TransitionInfo(
    val from: String,
    val to: String,
    @JsonDeserialize(using = TransitionConditionDeserializer::class)
    val condition: TransitionCondition,
    val priority: Int = 0,
    val description: String = "",
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 函数信息
 */
data class FunctionInfo(
    val stateId: String,
    val functionName: String,
    val className: String? = null,
    val description: String = "",
    val priority: Int = 0,
    val timeout: Long = 0L,
    val retryCount: Int = 0,
    val async: Boolean = false,
    val tags: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 状态类型
 */
enum class StateType {
    INITIAL,    // 初始状态
    NORMAL,     // 普通状态
    FINAL,      // 结束状态
    ERROR       // 错误状态
}

/**
 * 转移条件
 */
sealed class TransitionCondition {
    object Success : TransitionCondition()
    object Failure : TransitionCondition()
    data class Custom(val predicate: String) : TransitionCondition()
}

/**
 * 状态定义解析器
 */
object StateDefinitionParser {
    
    /**
     * 解析状态定义
     */
    fun parse(format: StateDefinitionFormat, content: String): StateFlow {
        val definition = format.parse(content)
        // 验证配置
        validateConfig(definition)
        return buildStateFlow(definition)
    }
    
    /**
     * 验证配置的合理性
     */
    private fun validateConfig(definition: StateDefinition) {
        val stateIds = definition.states.map { it.id }.toSet()
        
        definition.states.forEach { state ->
            state.timeout?.let { timeout ->
                require(timeout == -1L || timeout > 0) {
                    "State '${state.id}': timeout must be -1 (unlimited) or positive, got: $timeout"
                }
            }
            
            // 验证超时操作配置
            state.timeoutAction?.let { action ->
                require(action.type in listOf("transition", "event")) {
                    "State '${state.id}': timeoutAction.type must be 'transition' or 'event', got: ${action.type}"
                }
                if (action.type == "transition") {
                    require(!action.targetState.isNullOrBlank()) {
                        "State '${state.id}': timeoutAction.targetState is required when type is 'transition'"
                    }
                    // 验证目标状态存在
                    require(action.targetState in stateIds) {
                        "State '${state.id}': timeoutAction.targetState '${action.targetState}' does not exist in workflow states"
                    }
                } else if (action.type == "event") {
                    require(!action.eventType.isNullOrBlank()) {
                        "State '${state.id}': timeoutAction.eventType is required when type is 'event'"
                    }
                }
            }
        }
    }
    
    /**
     * 构建状态流
     */
    private fun buildStateFlow(definition: StateDefinition): StateFlow {
        val flow = StateFlow()
        
        // 应用流程级别配置
        definition.config?.let { config ->
            flow.config(
                pauseable = config.pauseable,
                defaultTimeout = config.defaultTimeout,
                autoResume = config.autoResume
            )
        }
        
        // 添加状态（包含状态级别的配置）
        definition.states.forEach { stateInfo ->
            // 转换 TimeoutAction
            val timeoutAction = stateInfo.timeoutAction?.let { action ->
                StateFlow.TimeoutAction(
                    type = action.type,
                    targetState = action.targetState,
                    eventType = action.eventType,
                    eventName = action.eventName
                )
            }
            
            // 转换执行模式
            val executionMode = convertExecutionMode(stateInfo.executionMode)
            
            // 转换分支配置
            val branchConfig = stateInfo.branchConfig?.map { branchInfo ->
                BranchConfiguration(
                    name = branchInfo.name,
                    targetStateId = branchInfo.targetStateId,
                    condition = convertCondition(branchInfo.condition),
                    priority = branchInfo.priority,
                    description = branchInfo.description,
                    metadata = branchInfo.metadata
                )
            }
            
            // 转换并行配置
            val parallelConfig = stateInfo.parallelConfig?.let { parallelInfo ->
                ParallelConfiguration(
                    branches = parallelInfo.branches.map { branchInfo ->
                        ParallelBranch(
                            branchId = branchInfo.branchId,
                            targetStateId = branchInfo.targetStateId,
                            description = branchInfo.description,
                            metadata = branchInfo.metadata
                        )
                    },
                    waitStrategy = convertWaitStrategy(parallelInfo.waitStrategy),
                    timeout = parallelInfo.timeout,
                    errorStrategy = convertErrorStrategy(parallelInfo.errorStrategy)
                )
            }
            
            flow.state(
                id = stateInfo.id,
                name = stateInfo.name,
                type = convertStateType(stateInfo.type),
                parentId = stateInfo.parentId,
                isInitial = stateInfo.isInitial,
                isFinal = stateInfo.isFinal,
                isError = stateInfo.isError,
                pauseable = stateInfo.pauseable,
                timeout = stateInfo.timeout,
                pauseOnEnter = stateInfo.pauseOnEnter,
                timeoutAction = timeoutAction,
                executionMode = executionMode,
                branchConfig = branchConfig,
                parallelConfig = parallelConfig
            )
        }
        
        // 添加转移
        definition.transitions.forEach { transitionInfo ->
            flow.transition(
                from = transitionInfo.from,
                to = transitionInfo.to,
                condition = convertTransitionCondition(transitionInfo.condition),
                priority = transitionInfo.priority
            )
        }
        
        return flow
    }
    
    /**
     * 转换状态类型
     */
    private fun convertStateType(type: StateType): StateFlow.StateType {
        return when (type) {
            StateType.INITIAL -> StateFlow.StateType.INITIAL
            StateType.NORMAL -> StateFlow.StateType.NORMAL
            StateType.FINAL -> StateFlow.StateType.FINAL
            StateType.ERROR -> StateFlow.StateType.ERROR
        }
    }
    
    /**
     * 转换转移条件
     */
    private fun convertTransitionCondition(condition: TransitionCondition): StateFlow.TransitionCondition {
        return when (condition) {
            is TransitionCondition.Success -> StateFlow.TransitionCondition.Success
            is TransitionCondition.Failure -> StateFlow.TransitionCondition.Failure
            is TransitionCondition.Custom -> StateFlow.TransitionCondition.Custom { result ->
                // 这里可以解析自定义条件
                true
            }
        }
    }
    
    /**
     * 转换执行模式
     */
    private fun convertExecutionMode(mode: String?): ExecutionMode {
        return when (mode?.uppercase()) {
            "PARALLEL" -> ExecutionMode.PARALLEL
            "CONDITIONAL" -> ExecutionMode.CONDITIONAL
            "JOIN" -> ExecutionMode.JOIN
            "SEQUENTIAL", null -> ExecutionMode.SEQUENTIAL
            else -> ExecutionMode.SEQUENTIAL
        }
    }
    
    /**
     * 转换条件信息
     */
    private fun convertCondition(conditionInfo: ConditionInfo): ContextConditionEvaluator {
        return when (conditionInfo.type.uppercase()) {
            "LOCAL_STATE" -> {
                requireNotNull(conditionInfo.key) { "Condition key is required for LOCAL_STATE" }
                requireNotNull(conditionInfo.operator) { "Condition operator is required for LOCAL_STATE" }
                ContextConditionEvaluatorBuilder.createLocalStateCondition(
                    key = conditionInfo.key,
                    operator = convertComparisonOperator(conditionInfo.operator),
                    value = requireNotNull(conditionInfo.value) { "Condition value is required for LOCAL_STATE" }
                )
            }
            "GLOBAL_STATE" -> {
                requireNotNull(conditionInfo.key) { "Condition key is required for GLOBAL_STATE" }
                requireNotNull(conditionInfo.operator) { "Condition operator is required for GLOBAL_STATE" }
                ContextConditionEvaluatorBuilder.createGlobalStateCondition(
                    key = conditionInfo.key,
                    operator = convertComparisonOperator(conditionInfo.operator),
                    value = requireNotNull(conditionInfo.value) { "Condition value is required for GLOBAL_STATE" }
                )
            }
            "EXPRESSION" -> {
                requireNotNull(conditionInfo.expression) { "Condition expression is required for EXPRESSION" }
                val exprType = when (conditionInfo.expressionType?.uppercase()) {
                    "JSON_PATH" -> ExpressionConditionEvaluator.ExpressionType.JSON_PATH
                    "SIMPLE" -> ExpressionConditionEvaluator.ExpressionType.SIMPLE
                    "GROOVY" -> ExpressionConditionEvaluator.ExpressionType.GROOVY
                    else -> ExpressionConditionEvaluator.ExpressionType.SIMPLE
                }
                ContextConditionEvaluatorBuilder.createExpression(exprType, conditionInfo.expression)
            }
            "COMPOSITE" -> {
                requireNotNull(conditionInfo.logicalOperator) { "Logical operator is required for COMPOSITE" }
                requireNotNull(conditionInfo.conditions) { "Sub-conditions are required for COMPOSITE" }
                ContextConditionEvaluatorBuilder.createComposite(
                    operator = convertLogicalOperator(conditionInfo.logicalOperator),
                    *conditionInfo.conditions.map { convertCondition(it) }.toTypedArray()
                )
            }
            else -> throw IllegalArgumentException("Unknown condition type: ${conditionInfo.type}")
        }
    }
    
    /**
     * 转换比较操作符
     */
    private fun convertComparisonOperator(operator: String): ComparisonOperator {
        return when (operator.uppercase()) {
            "EQ", "EQUAL", "==" -> ComparisonOperator.EQ
            "NE", "NOT_EQUAL", "!=" -> ComparisonOperator.NE
            "GT", "GREATER_THAN", ">" -> ComparisonOperator.GT
            "GE", "GREATER_EQUAL", ">=" -> ComparisonOperator.GE
            "LT", "LESS_THAN", "<" -> ComparisonOperator.LT
            "LE", "LESS_EQUAL", "<=" -> ComparisonOperator.LE
            "IN" -> ComparisonOperator.IN
            "CONTAINS" -> ComparisonOperator.CONTAINS
            else -> throw IllegalArgumentException("Unknown comparison operator: $operator")
        }
    }
    
    /**
     * 转换逻辑操作符
     */
    private fun convertLogicalOperator(operator: String): LogicalOperator {
        return when (operator.uppercase()) {
            "AND", "&&" -> LogicalOperator.AND
            "OR", "||" -> LogicalOperator.OR
            "NOT", "!" -> LogicalOperator.NOT
            else -> throw IllegalArgumentException("Unknown logical operator: $operator")
        }
    }
    
    /**
     * 转换等待策略
     */
    private fun convertWaitStrategy(strategy: String): ParallelWaitStrategy {
        return when (strategy.uppercase()) {
            "ALL" -> ParallelWaitStrategy.ALL
            "ANY" -> ParallelWaitStrategy.ANY
            "N_COUNT", "N" -> ParallelWaitStrategy.N_COUNT
            else -> ParallelWaitStrategy.ALL
        }
    }
    
    /**
     * 转换错误处理策略
     */
    private fun convertErrorStrategy(strategy: String): ParallelErrorStrategy {
        return when (strategy.uppercase()) {
            "FAIL_ALL", "FAIL" -> ParallelErrorStrategy.FAIL_ALL
            "IGNORE_FAILURES", "IGNORE" -> ParallelErrorStrategy.IGNORE_FAILURES
            "TOLERATE_FAILURES", "TOLERATE" -> ParallelErrorStrategy.TOLERATE_FAILURES
            else -> ParallelErrorStrategy.FAIL_ALL
        }
    }
}
