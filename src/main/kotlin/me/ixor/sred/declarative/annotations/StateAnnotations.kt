package me.ixor.sred.declarative.annotations

import me.ixor.sred.core.StateContext
import me.ixor.sred.declarative.StateResult

/**
 * 状态处理器注解
 * 用于标记处理特定状态的函数
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class StateHandler(
    val stateId: String,
    val description: String = "",
    val priority: Int = 0,
    val timeout: Long = 0L, // 超时时间（毫秒），0表示无超时
    val retryCount: Int = 0, // 重试次数
    val async: Boolean = false, // 是否异步执行
    val tags: Array<String> = [], // 标签
    val metadata: Array<String> = [] // 自定义元数据，格式为 "key=value"
)

/**
 * 状态前置处理器注解
 * 在进入状态前执行
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class StatePreHandler(
    val stateId: String,
    val description: String = "",
    val priority: Int = 0
)

/**
 * 状态后置处理器注解
 * 在状态执行完成后执行
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class StatePostHandler(
    val stateId: String,
    val description: String = "",
    val priority: Int = 0
)

/**
 * 状态错误处理器注解
 * 处理状态执行过程中的错误
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class StateErrorHandler(
    val stateId: String,
    val description: String = "",
    val priority: Int = 0
)

/**
 * 状态转移处理器注解
 * 处理状态转移逻辑
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class StateTransitionHandler(
    val fromStateId: String,
    val toStateId: String,
    val condition: String = "success", // success, failure, custom
    val description: String = "",
    val priority: Int = 0
)

/**
 * 状态元数据注解
 * 为状态定义额外的元数据
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class StateMetadata(
    val version: String = "1.0.0",
    val author: String = "",
    val created: String = "",
    val updated: String = "",
    val tags: Array<String> = [],
    val description: String = "",
    val documentation: String = "",
    val examples: Array<String> = [],
    val dependencies: Array<String> = [],
    val configuration: Array<String> = [] // 配置项，格式为 "key=value"
)

/**
 * 状态函数信息
 * 包含函数的完整元数据信息
 */
data class StateFunctionInfo(
    val functionName: String,
    val stateId: String,
    val description: String,
    val priority: Int,
    val timeout: Long,
    val retryCount: Int,
    val async: Boolean,
    val tags: List<String>,
    val metadata: Map<String, String>,
    val handlerType: HandlerType,
    val fromStateId: String? = null,
    val toStateId: String? = null,
    val condition: String? = null
)

/**
 * 处理器类型
 */
enum class HandlerType {
    STATE_HANDLER,      // 状态处理器
    PRE_HANDLER,        // 前置处理器
    POST_HANDLER,       // 后置处理器
    ERROR_HANDLER,      // 错误处理器
    TRANSITION_HANDLER  // 转移处理器
}

/**
 * 状态函数类型
 */
typealias StateFunction = suspend (StateContext) -> StateResult

/**
 * 状态前置函数类型
 */
typealias StatePreFunction = suspend (StateContext) -> Unit

/**
 * 状态后置函数类型
 */
typealias StatePostFunction = suspend (StateContext, StateResult) -> Unit

/**
 * 状态错误函数类型
 */
typealias StateErrorFunction = suspend (StateContext, Throwable) -> StateResult

/**
 * 状态转移函数类型
 */
typealias StateTransitionFunction = suspend (StateContext, StateResult) -> Boolean
