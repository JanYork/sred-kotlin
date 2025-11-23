package me.ixor.sred.declarative.annotations

import me.ixor.sred.core.*
import me.ixor.sred.declarative.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.callSuspend

/**
 * 注解处理器
 * 处理带注解的函数，自动绑定到状态
 */
object AnnotationProcessor {
    
    private val functionInfoMap = mutableMapOf<String, FunctionInfo>()
    private val functionMap = mutableMapOf<String, StateFunction>()
    
    /**
     * 处理带注解的类
     */
    fun processAnnotatedClass(instance: Any) {
        val clazz = instance::class
        val functions = clazz.members.filterIsInstance<KFunction<*>>()
        
        for (function in functions) {
            val annotation = function.findAnnotation<StateHandler>()
            if (annotation != null) {
                val stateId = annotation.stateId
                val description = annotation.description
                
                // 创建函数信息
                val functionInfo = FunctionInfo(
                    stateId = stateId,
                    functionName = function.name,
                    className = clazz.simpleName,
                    description = description
                )
                functionInfoMap[stateId] = functionInfo
                
                // 创建状态函数
                val stateFunction: StateFunction = { context ->
                    try {
                        val result = function.callSuspend(instance, context)
                        result as? StateResult ?: StateResult(true)
                    } catch (e: Exception) {
                        StateResult(false, error = e)
                    }
                }
                functionMap[stateId] = stateFunction
            }
        }
    }
    
    /**
     * 获取所有函数信息
     */
    fun getAllFunctionInfo(): Map<String, FunctionInfo> = functionInfoMap.toMap()
    
    /**
     * 获取状态函数
     */
    fun getStateFunction(stateId: String): StateFunction? = functionMap[stateId]
    
    /**
     * 清空处理器
     */
    fun clear() {
        functionInfoMap.clear()
        functionMap.clear()
    }
}

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