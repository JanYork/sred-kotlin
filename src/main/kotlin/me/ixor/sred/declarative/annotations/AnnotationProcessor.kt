package me.ixor.sred.declarative.annotations

import me.ixor.sred.core.StateContext
import me.ixor.sred.declarative.StateResult
import kotlinx.coroutines.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.callSuspend
import java.util.concurrent.ConcurrentHashMap

/**
 * 注解处理器
 * 自动扫描和绑定带注解的函数
 */
object AnnotationProcessor {
    
    private val registeredFunctions = ConcurrentHashMap<String, StateFunctionInfo>()
    private val functionImplementations = ConcurrentHashMap<String, StateFunction>()
    private val preHandlers = ConcurrentHashMap<String, MutableList<StatePreFunction>>()
    private val postHandlers = ConcurrentHashMap<String, MutableList<StatePostFunction>>()
    private val errorHandlers = ConcurrentHashMap<String, MutableList<StateErrorFunction>>()
    private val transitionHandlers = ConcurrentHashMap<String, MutableList<StateTransitionFunction>>()
    
    /**
     * 处理带注解的类
     */
    fun processAnnotatedClass(instance: Any) {
        val clazz = instance::class
        val functions = clazz.members.filterIsInstance<KFunction<*>>()
        
        for (function in functions) {
            processStateHandler(instance, function)
            processPreHandler(instance, function)
            processPostHandler(instance, function)
            processErrorHandler(instance, function)
            processTransitionHandler(instance, function)
        }
    }
    
    /**
     * 处理状态处理器注解
     */
    private fun processStateHandler(instance: Any, function: KFunction<*>) {
        val annotation = function.findAnnotation<StateHandler>()
        if (annotation != null) {
            val functionInfo = StateFunctionInfo(
                functionName = function.name,
                stateId = annotation.stateId,
                description = annotation.description,
                priority = annotation.priority,
                timeout = annotation.timeout,
                retryCount = annotation.retryCount,
                async = annotation.async,
                tags = annotation.tags.toList(),
                metadata = parseMetadata(annotation.metadata),
                handlerType = HandlerType.STATE_HANDLER
            )
            
            val stateFunction: StateFunction = { context ->
                try {
                    val result = function.callSuspend(instance, context)
                    result as? StateResult ?: StateResult(true)
                } catch (e: Exception) {
                    StateResult(false, error = e)
                }
            }
            
            registeredFunctions[annotation.stateId] = functionInfo
            functionImplementations[annotation.stateId] = stateFunction
            
            println("   📌 注册状态处理器: ${annotation.stateId} -> ${function.name}")
        }
    }
    
    /**
     * 处理前置处理器注解
     */
    private fun processPreHandler(instance: Any, function: KFunction<*>) {
        val annotation = function.findAnnotation<StatePreHandler>()
        if (annotation != null) {
            val preFunction: StatePreFunction = { context ->
                function.callSuspend(instance, context)
            }
            
            preHandlers.computeIfAbsent(annotation.stateId) { mutableListOf() }
                .add(preFunction)
            
            println("   🔄 注册前置处理器: ${annotation.stateId} -> ${function.name}")
        }
    }
    
    /**
     * 处理后置处理器注解
     */
    private fun processPostHandler(instance: Any, function: KFunction<*>) {
        val annotation = function.findAnnotation<StatePostHandler>()
        if (annotation != null) {
            val postFunction: StatePostFunction = { context, result ->
                function.callSuspend(instance, context, result)
            }
            
            postHandlers.computeIfAbsent(annotation.stateId) { mutableListOf() }
                .add(postFunction)
            
            println("   🔄 注册后置处理器: ${annotation.stateId} -> ${function.name}")
        }
    }
    
    /**
     * 处理错误处理器注解
     */
    private fun processErrorHandler(instance: Any, function: KFunction<*>) {
        val annotation = function.findAnnotation<StateErrorHandler>()
        if (annotation != null) {
            val errorFunction: StateErrorFunction = { context, error ->
                val result = function.callSuspend(instance, context, error)
                result as? StateResult ?: StateResult(false, error = error)
            }
            
            errorHandlers.computeIfAbsent(annotation.stateId) { mutableListOf() }
                .add(errorFunction)
            
            println("   🚨 注册错误处理器: ${annotation.stateId} -> ${function.name}")
        }
    }
    
    /**
     * 处理转移处理器注解
     */
    private fun processTransitionHandler(instance: Any, function: KFunction<*>) {
        val annotation = function.findAnnotation<StateTransitionHandler>()
        if (annotation != null) {
            val transitionFunction: StateTransitionFunction = { context, result ->
                val transitionResult = function.callSuspend(instance, context, result)
                transitionResult as? Boolean ?: false
            }
            
            val key = "${annotation.fromStateId}->${annotation.toStateId}"
            transitionHandlers.computeIfAbsent(key) { mutableListOf() }
                .add(transitionFunction)
            
            println("   🔀 注册转移处理器: $key -> ${function.name}")
        }
    }
    
    /**
     * 解析元数据
     */
    private fun parseMetadata(metadata: Array<String>): Map<String, String> {
        return metadata.associate { 
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
        }
    }
    
    /**
     * 获取状态函数
     */
    fun getStateFunction(stateId: String): StateFunction? = functionImplementations[stateId]
    
    /**
     * 获取前置处理器
     */
    fun getPreHandlers(stateId: String): List<StatePreFunction> = preHandlers[stateId] ?: emptyList()
    
    /**
     * 获取后置处理器
     */
    fun getPostHandlers(stateId: String): List<StatePostFunction> = postHandlers[stateId] ?: emptyList()
    
    /**
     * 获取错误处理器
     */
    fun getErrorHandlers(stateId: String): List<StateErrorFunction> = errorHandlers[stateId] ?: emptyList()
    
    /**
     * 获取转移处理器
     */
    fun getTransitionHandlers(fromStateId: String, toStateId: String): List<StateTransitionFunction> {
        val key = "$fromStateId->$toStateId"
        return transitionHandlers[key] ?: emptyList()
    }
    
    /**
     * 获取函数信息
     */
    fun getFunctionInfo(stateId: String): StateFunctionInfo? = registeredFunctions[stateId]
    
    /**
     * 获取所有注册的函数信息
     */
    fun getAllFunctionInfo(): List<StateFunctionInfo> = registeredFunctions.values.toList()
    
    /**
     * 清空注册
     */
    fun clear() {
        registeredFunctions.clear()
        functionImplementations.clear()
        preHandlers.clear()
        postHandlers.clear()
        errorHandlers.clear()
        transitionHandlers.clear()
    }
    
    /**
     * 打印注册摘要
     */
    fun printRegistrationSummary() {
        println("\n📊 注解处理器注册摘要:")
        println("   状态处理器: ${functionImplementations.size}")
        println("   前置处理器: ${preHandlers.values.sumOf { it.size }}")
        println("   后置处理器: ${postHandlers.values.sumOf { it.size }}")
        println("   错误处理器: ${errorHandlers.values.sumOf { it.size }}")
        println("   转移处理器: ${transitionHandlers.values.sumOf { it.size }}")
        
        if (registeredFunctions.isNotEmpty()) {
            println("\n📋 注册的状态处理器详情:")
            registeredFunctions.values.sortedBy { it.priority }.forEach { info ->
                println("   • ${info.stateId}: ${info.description}")
                println("     函数: ${info.functionName}, 优先级: ${info.priority}")
                if (info.tags.isNotEmpty()) {
                    println("     标签: ${info.tags.joinToString(", ")}")
                }
                if (info.metadata.isNotEmpty()) {
                    println("     元数据: ${info.metadata}")
                }
            }
        }
    }
}
