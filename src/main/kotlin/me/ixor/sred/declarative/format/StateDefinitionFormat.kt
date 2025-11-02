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
    val timeoutAction: TimeoutAction? = null  // 超时后的操作
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
                timeoutAction = timeoutAction
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
}
