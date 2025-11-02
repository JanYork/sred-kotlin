package me.ixor.sred.declarative

import me.ixor.sred.core.*
import me.ixor.sred.declarative.annotations.StateHandler
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.callSuspend

/**
 * 声明式函数注册器
 */
object FunctionRegistry {
    
    internal val registeredFunctions = mutableMapOf<String, StateFunction>()
    
    /**
     * 自动注册带注解的函数
     */
    fun registerFunctions(instance: Any) {
        val clazz = instance::class
        val functions = clazz.members.filterIsInstance<KFunction<*>>()
        
        for (function in functions) {
            val annotation = function.findAnnotation<StateHandler>()
            if (annotation != null) {
                val stateId = annotation.stateId
                val stateFunction: StateFunction = { context ->
                    try {
                        val result = function.callSuspend(instance, context)
                        result as? StateResult ?: StateResult(true)
                    } catch (e: Exception) {
                        StateResult(false, error = e)
                    }
                }
                registerFunction(stateId, stateFunction)
            }
        }
    }
    
    /**
     * 手动注册函数
     */
    fun registerFunction(stateId: String, function: StateFunction) {
        registeredFunctions[stateId] = function
    }
    
    /**
     * 获取函数
     */
    fun getFunction(stateId: String): StateFunction? = registeredFunctions[stateId]
    
    /**
     * 清空注册
     */
    fun clear() {
        registeredFunctions.clear()
    }
}

/**
 * 状态流构建器扩展
 * 自动绑定带注解的函数到状态流
 */
fun StateFlow.bindFunctions(service: Any) {
    FunctionRegistry.registerFunctions(service)
    
    // 将注册的函数绑定到状态
    FunctionRegistry.registeredFunctions.forEach { (stateId, function) ->
        this.bind(stateId, function)
    }
}
