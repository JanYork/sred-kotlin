package me.ixor.sred.declarative.format

import me.ixor.sred.core.*
import me.ixor.sred.declarative.*
import com.fasterxml.jackson.module.kotlin.*
import com.fasterxml.jackson.databind.ObjectMapper

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
    val metadata: Map<String, Any> = emptyMap()
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
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 转移信息
 */
data class TransitionInfo(
    val from: String,
    val to: String,
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
        return buildStateFlow(definition)
    }
    
    /**
     * 构建状态流
     */
    private fun buildStateFlow(definition: StateDefinition): StateFlow {
        val flow = StateFlow()
        
        // 添加状态
        definition.states.forEach { stateInfo ->
            flow.state(
                id = stateInfo.id,
                name = stateInfo.name,
                type = convertStateType(stateInfo.type),
                parentId = stateInfo.parentId,
                isInitial = stateInfo.isInitial,
                isFinal = stateInfo.isFinal,
                isError = stateInfo.isError
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
