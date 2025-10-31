package me.ixor.sred.declarative

import me.ixor.sred.core.*
import kotlinx.coroutines.*

/**
 * StateMachine构建器 - 支持链式配置状态机
 * 
 * 使用构建器模式简化StateMachine的创建和配置
 */
class StateMachineBuilder {
    private val flow = StateFlow()
    private var instanceIdGenerator: (() -> String)? = null
    
    /**
     * 添加状态 - 链式调用
     */
    fun state(
        id: String,
        name: String,
        type: StateFlow.StateType = StateFlow.StateType.NORMAL,
        parentId: String? = null,
        isInitial: Boolean = false,
        isFinal: Boolean = false,
        isError: Boolean = false
    ): StateMachineBuilder {
        flow.state(id, name, type, parentId, isInitial, isFinal, isError)
        return this
    }
    
    /**
     * 添加转移 - 链式调用
     */
    fun transition(
        from: String,
        to: String,
        condition: StateFlow.TransitionCondition = StateFlow.TransitionCondition.Success,
        priority: Int = 0
    ): StateMachineBuilder {
        flow.transition(from, to, condition, priority)
        return this
    }
    
    /**
     * 绑定函数 - 链式调用
     */
    fun bind(id: String, function: StateFunction): StateMachineBuilder {
        flow.bind(id, function)
        return this
    }
    
    /**
     * 绑定函数引用 - 链式调用
     */
    fun bindFunction(id: String, functionRef: suspend (StateContext) -> StateResult): StateMachineBuilder {
        flow.bindFunction(id, functionRef)
        return this
    }
    
    /**
     * 批量绑定函数 - 链式调用
     */
    fun bindFunctions(vararg bindings: Pair<String, StateFunction>): StateMachineBuilder {
        flow.bindFunctions(*bindings)
        return this
    }
    
    /**
     * 使用DSL绑定函数 - 链式调用
     */
    fun bindFunctions(binding: FunctionBindingDSL.() -> Unit): StateMachineBuilder {
        flow.bindFunctions(binding)
        return this
    }
    
    /**
     * 设置实例ID生成器
     */
    fun withInstanceIdGenerator(generator: () -> String): StateMachineBuilder {
        this.instanceIdGenerator = generator
        return this
    }
    
    /**
     * 构建StateMachine
     */
    fun build(): StateMachine {
        return flow.build()
    }
    
    /**
     * 构建StateMachine并启动实例
     */
    suspend fun buildAndStart(
        instanceId: String? = null,
        initialContext: StateContext = StateContextFactory.create()
    ): StateMachineInstance {
        val machine = build()
        val id = instanceId ?: instanceIdGenerator?.invoke() ?: "instance_${System.currentTimeMillis()}"
        return machine.start(id, initialContext)
    }
    
    companion object {
        fun create(): StateMachineBuilder = StateMachineBuilder()
    }
}

/**
 * DSL函数：快速创建StateMachineBuilder
 */
fun stateMachine(block: StateMachineBuilder.() -> Unit): StateMachineBuilder {
    val builder = StateMachineBuilder.create()
    builder.block()
    return builder
}

