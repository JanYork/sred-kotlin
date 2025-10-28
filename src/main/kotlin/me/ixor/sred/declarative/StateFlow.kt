package me.ixor.sred.declarative

import me.ixor.sred.core.*
import me.ixor.sred.declarative.annotations.AnnotationProcessor
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 状态结果
 */
data class StateResult(
    val success: Boolean,
    val data: Map<String, Any> = emptyMap(),
    val error: Throwable? = null
) {
    companion object {
        fun success(data: Map<String, Any> = emptyMap()): StateResult = StateResult(true, data)
        fun failure(message: String, error: Throwable? = null): StateResult = StateResult(false, error = error ?: RuntimeException(message))
    }
}

/**
 * 状态函数
 */
typealias StateFunction = suspend (StateContext) -> StateResult

/**
 * 声明式状态流转定义
 * 支持状态声明、函数绑定和自动流转
 */
class StateFlow {
    
    internal val states = ConcurrentHashMap<String, StateDefinition>()
    internal val transitions = ConcurrentHashMap<String, MutableList<TransitionDefinition>>()
    internal val functions = ConcurrentHashMap<String, StateFunction>()
    
    /**
     * 状态定义
     */
    data class StateDefinition(
        val id: String,
        val name: String,
        val type: StateType,
        val parentId: String? = null,
        val isInitial: Boolean = false,
        val isFinal: Boolean = false,
        val isError: Boolean = false
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
     * 转移定义
     */
    data class TransitionDefinition(
        val from: String,
        val to: String,
        val condition: TransitionCondition,
        val priority: Int = 0
    )
    
    /**
     * 转移条件
     */
    sealed class TransitionCondition {
        object Success : TransitionCondition()
        object Failure : TransitionCondition()
        data class Custom(val predicate: (StateResult) -> Boolean) : TransitionCondition()
    }
    
    
    /**
     * 声明状态
     */
    fun state(
        id: String,
        name: String,
        type: StateType = StateType.NORMAL,
        parentId: String? = null,
        isInitial: Boolean = false,
        isFinal: Boolean = false,
        isError: Boolean = false
    ) {
        states[id] = StateDefinition(id, name, type, parentId, isInitial, isFinal, isError)
    }
    
    /**
     * 声明转移
     */
    fun transition(
        from: String,
        to: String,
        condition: TransitionCondition = TransitionCondition.Success,
        priority: Int = 0
    ) {
        transitions.computeIfAbsent(from) { mutableListOf() }
            .add(TransitionDefinition(from, to, condition, priority))
    }
    
    /**
     * 绑定函数到状态
     */
    fun bind(id: String, function: StateFunction) {
        functions[id] = function
    }
    
    /**
     * 绑定函数引用到状态
     */
    fun bindFunction(id: String, functionRef: suspend (StateContext) -> StateResult) {
        functions[id] = functionRef
    }
    
    /**
     * 批量绑定函数
     */
    fun bindFunctions(vararg bindings: Pair<String, StateFunction>) {
        bindings.forEach { (stateId, function) ->
            functions[stateId] = function
        }
    }
    
    /**
     * 使用DSL绑定函数
     */
    fun bindFunctions(binding: FunctionBindingDSL.() -> Unit) {
        val dsl = FunctionBindingDSL()
        dsl.binding()
        
        // 将绑定的函数注册到状态流
        dsl.getAllFunctions().forEach { (stateId, function) ->
            this.bind(stateId, function)
        }
    }
    
    /**
     * 使用注解处理器绑定函数
     */
    fun bindAnnotatedFunctions(instance: Any) {
        AnnotationProcessor.processAnnotatedClass(instance)
        
        // 将注解处理器中的函数绑定到状态流
        AnnotationProcessor.getAllFunctionInfo().forEach { (stateId, info) ->
            val function = AnnotationProcessor.getStateFunction(stateId)
            if (function != null) {
                this.bind(stateId, function)
            }
        }
    }
    
    /**
     * 构建状态机
     */
    fun build(): StateMachine {
        return StateMachine(this)
    }
}

/**
 * 状态机引擎
 */
class StateMachine(private val flow: StateFlow) {
    
    private val currentStates = ConcurrentHashMap<String, String>() // 实例ID -> 当前状态ID
    private val contexts = ConcurrentHashMap<String, StateContext>() // 实例ID -> 上下文
    
    /**
     * 启动状态机实例
     */
    suspend fun start(instanceId: String, initialContext: StateContext = StateContextFactory.create()): StateMachineInstance {
        val initialState = flow.states.values.find { it.isInitial }
            ?: throw IllegalStateException("No initial state defined")
        
        currentStates[instanceId] = initialState.id
        contexts[instanceId] = initialContext
        
        return StateMachineInstance(this, instanceId)
    }
    
    /**
     * 处理事件
     */
    suspend fun processEvent(instanceId: String, event: Event): StateResult {
        val currentStateId = currentStates[instanceId] ?: throw IllegalStateException("Instance not found: $instanceId")
        val context = contexts[instanceId] ?: throw IllegalStateException("Context not found: $instanceId")
        
        val stateDef = flow.states[currentStateId] ?: throw IllegalStateException("State not found: $currentStateId")
        
        // 执行状态函数
        val function = flow.functions[currentStateId]
        val result = if (function != null) {
            try {
                function(context)
            } catch (e: Exception) {
                StateResult(false, error = e)
            }
        } else {
            StateResult(true) // 没有函数的状态直接成功
        }
        
        // 更新上下文
        val newContext = StateContextFactory.create(
            localState = context.localState + result.data,
            globalState = context.globalState
        )
        contexts[instanceId] = newContext
        
        // 查找下一个状态
        val nextStateId = findNextState(currentStateId, result)
        if (nextStateId != null) {
            currentStates[instanceId] = nextStateId
        }
        
        return result
    }
    
    /**
     * 获取当前状态
     */
    fun getCurrentState(instanceId: String): String? = currentStates[instanceId]
    
    /**
     * 获取上下文
     */
    fun getContext(instanceId: String): StateContext? = contexts[instanceId]
    
    /**
     * 查找下一个状态
     */
    private fun findNextState(currentStateId: String, result: StateResult): String? {
        val possibleTransitions = flow.transitions[currentStateId] ?: return null
        
        // 按优先级排序
        val sortedTransitions = possibleTransitions.sortedByDescending { it.priority }
        
        for (transition in sortedTransitions) {
            val matches = when (transition.condition) {
                is StateFlow.TransitionCondition.Success -> result.success
                is StateFlow.TransitionCondition.Failure -> !result.success
                is StateFlow.TransitionCondition.Custom -> transition.condition.predicate(result)
            }
            
            if (matches) {
                return transition.to
            }
        }
        
        return null
    }
}

/**
 * 状态机实例
 */
class StateMachineInstance(
    private val machine: StateMachine,
    private val instanceId: String
) {
    
    /**
     * 处理事件
     */
    suspend fun processEvent(event: Event): StateResult {
        return machine.processEvent(instanceId, event)
    }
    
    /**
     * 获取当前状态
     */
    fun getCurrentState(): String? = machine.getCurrentState(instanceId)
    
    /**
     * 获取上下文
     */
    fun getContext(): StateContext? = machine.getContext(instanceId)
}

/**
 * 状态流构建器
 */
object StateFlowBuilder {
    
    /**
     * 创建用户注册状态流
     */
    fun createUserRegistrationFlow(): StateFlow {
        val flow = StateFlow()
        
        // 主状态定义
        flow.state("user_unregistered", "用户未注册", StateFlow.StateType.INITIAL, isInitial = true)
        flow.state("user_registering", "用户正在注册", StateFlow.StateType.NORMAL)
        flow.state("user_registered", "用户注册成功", StateFlow.StateType.FINAL, isFinal = true)
        flow.state("user_registration_failed", "用户注册失败", StateFlow.StateType.ERROR, isError = true)
        
        // 子状态定义（注册过程中的子状态）
        flow.state("registration_not_submitted", "未提交注册", StateFlow.StateType.INITIAL, "user_registering", isInitial = true)
        flow.state("registration_validating", "参数校验", StateFlow.StateType.NORMAL, "user_registering")
        flow.state("registration_storing", "存储用户信息", StateFlow.StateType.NORMAL, "user_registering")
        flow.state("registration_completed", "注册完成", StateFlow.StateType.FINAL, "user_registering", isFinal = true)
        
        // 邮件发送子状态（注册成功后的子状态）
        flow.state("email_sending", "发送邮件", StateFlow.StateType.NORMAL, "user_registered")
        flow.state("email_sent", "邮件已发送", StateFlow.StateType.FINAL, "user_registered", isFinal = true)
        
        // 主状态转移
        flow.transition("user_unregistered", "user_registering", StateFlow.TransitionCondition.Success)
        flow.transition("user_registering", "user_registered", StateFlow.TransitionCondition.Success)
        flow.transition("user_registering", "user_registration_failed", StateFlow.TransitionCondition.Failure)
        
        // 子状态转移
        flow.transition("registration_not_submitted", "registration_validating", StateFlow.TransitionCondition.Success)
        flow.transition("registration_validating", "registration_storing", StateFlow.TransitionCondition.Success)
        flow.transition("registration_validating", "user_registration_failed", StateFlow.TransitionCondition.Failure)
        flow.transition("registration_storing", "registration_completed", StateFlow.TransitionCondition.Success)
        flow.transition("registration_storing", "user_registration_failed", StateFlow.TransitionCondition.Failure)
        flow.transition("registration_completed", "user_registered", StateFlow.TransitionCondition.Success)
        
        // 邮件发送转移
        flow.transition("user_registered", "email_sending", StateFlow.TransitionCondition.Success)
        flow.transition("email_sending", "email_sent", StateFlow.TransitionCondition.Success)
        
        return flow
    }
}
