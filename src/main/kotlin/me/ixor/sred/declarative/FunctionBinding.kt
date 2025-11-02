package me.ixor.sred.declarative

import me.ixor.sred.core.*

/**
 * 函数绑定DSL
 * 提供声明式的函数绑定语法
 */
class FunctionBindingDSL {
    
    private val functions = mutableMapOf<String, StateFunction>()
    
    /**
     * 绑定函数
     */
    fun bind(stateId: String, function: StateFunction) {
        functions[stateId] = function
    }
    
    /**
     * 绑定函数引用
     */
    fun bindFunction(stateId: String, functionRef: suspend (StateContext) -> StateResult) {
        functions[stateId] = functionRef
    }
    
    /**
     * 获取所有函数
     */
    fun getAllFunctions(): Map<String, StateFunction> = functions.toMap()
}

/**
 * 函数绑定扩展
 * 
 * 注意：业务相关的扩展函数应在业务层定义，不应在框架层
 * 框架只提供通用的 bind 和 bindFunction 方法
 */