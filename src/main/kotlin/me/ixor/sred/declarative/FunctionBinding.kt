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
 */
fun FunctionBindingDSL.userValidation(stateId: String, function: suspend (StateContext) -> StateResult) {
    bindFunction(stateId, function)
}

fun FunctionBindingDSL.userStorage(stateId: String, function: suspend (StateContext) -> StateResult) {
    bindFunction(stateId, function)
}

fun FunctionBindingDSL.emailNotification(stateId: String, function: suspend (StateContext) -> StateResult) {
    bindFunction(stateId, function)
}

fun FunctionBindingDSL.paymentProcessing(stateId: String, function: suspend (StateContext) -> StateResult) {
    bindFunction(stateId, function)
}

fun FunctionBindingDSL.shippingPreparation(stateId: String, function: suspend (StateContext) -> StateResult) {
    bindFunction(stateId, function)
}