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
    
    // 流程级别配置
    var pauseable: Boolean = false           // 流程是否支持长时间停顿
    var defaultTimeout: Long? = null         // 流程默认超时时间（秒）
    var autoResume: Boolean = false          // 是否自动恢复暂停的流程
    
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
     * 状态定义
     */
    data class StateDefinition(
        val id: String,
        val name: String,
        val type: StateType,
        val parentId: String? = null,
        val isInitial: Boolean = false,
        val isFinal: Boolean = false,
        val isError: Boolean = false,
        // 状态配置：长时间停顿和超时
        val pauseable: Boolean = false,      // 是否支持长时间停顿
        val timeout: Long? = null,           // 超时时间（秒），null 表示不超时，-1 表示无限久
        val pauseOnEnter: Boolean = false,   // 进入此状态时自动暂停
        val timeoutAction: TimeoutAction? = null  // 超时后的操作
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
     * 声明状态 - 支持链式调用
     */
    fun state(
        id: String,
        name: String,
        type: StateType = StateType.NORMAL,
        parentId: String? = null,
        isInitial: Boolean = false,
        isFinal: Boolean = false,
        isError: Boolean = false,
        pauseable: Boolean? = null,          // null 表示继承流程配置
        timeout: Long? = null,               // 超时时间（秒），null 表示使用流程默认值，-1 表示无限久
        pauseOnEnter: Boolean = false,       // 进入此状态时自动暂停
        timeoutAction: TimeoutAction? = null  // 超时后的操作
    ): StateFlow {
        val statePauseable = pauseable ?: this.pauseable
        // timeout 为 null 时使用流程级别的 defaultTimeout
        val stateTimeout = timeout ?: defaultTimeout
        states[id] = StateDefinition(id, name, type, parentId, isInitial, isFinal, isError, statePauseable, stateTimeout, pauseOnEnter, timeoutAction)
        return this
    }
    
    /**
     * 配置流程级别设置
     */
    fun config(
        pauseable: Boolean = false,
        defaultTimeout: Long? = null,
        autoResume: Boolean = false
    ): StateFlow {
        this.pauseable = pauseable
        this.defaultTimeout = defaultTimeout
        this.autoResume = autoResume
        return this
    }
    
    /**
     * 声明转移 - 支持链式调用
     */
    fun transition(
        from: String,
        to: String,
        condition: TransitionCondition = TransitionCondition.Success,
        priority: Int = 0
    ): StateFlow {
        transitions.computeIfAbsent(from) { mutableListOf() }
            .add(TransitionDefinition(from, to, condition, priority))
        return this
    }
    
    /**
     * 绑定函数到状态 - 支持链式调用
     */
    fun bind(id: String, function: StateFunction): StateFlow {
        functions[id] = function
        return this
    }
    
    /**
     * 绑定函数引用到状态 - 支持链式调用
     */
    fun bindFunction(id: String, functionRef: suspend (StateContext) -> StateResult): StateFlow {
        functions[id] = functionRef
        return this
    }
    
    /**
     * 批量绑定函数 - 支持链式调用
     */
    fun bindFunctions(vararg bindings: Pair<String, StateFunction>): StateFlow {
        bindings.forEach { (stateId, function) ->
            functions[stateId] = function
        }
        return this
    }
    
    /**
     * 使用DSL绑定函数 - 支持链式调用
     */
    fun bindFunctions(binding: FunctionBindingDSL.() -> Unit): StateFlow {
        val dsl = FunctionBindingDSL()
        dsl.binding()
        
        // 将绑定的函数注册到状态流
        dsl.getAllFunctions().forEach { (stateId, function) ->
            this.bind(stateId, function)
        }
        return this
    }
    
    /**
     * 使用注解处理器绑定函数 - 支持链式调用
     */
    fun bindAnnotatedFunctions(instance: Any): StateFlow {
        AnnotationProcessor.processAnnotatedClass(instance)
        
        // 将注解处理器中的函数绑定到状态流
        AnnotationProcessor.getAllFunctionInfo().forEach { (stateId, info) ->
            val function = AnnotationProcessor.getStateFunction(stateId)
            if (function != null) {
                this.bind(stateId, function)
            }
        }
        return this
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
        // 如果实例已存在（可能从持久化恢复），先恢复状态
        if (currentStates.containsKey(instanceId)) {
            return StateMachineInstance(this, instanceId)
        }
        
        // 确保初始上下文中设置了初始状态ID
        val initialStateId = initialContext.currentStateId ?: flow.states.values.firstOrNull { it.isInitial }?.id
        val contextWithState = if (initialStateId != null && initialContext.currentStateId == null) {
            initialContext.copy(currentStateId = initialStateId)
        } else {
            initialContext
        }
        val initialState = flow.states.values.find { it.isInitial }
            ?: throw IllegalStateException("No initial state defined")
        
        // 如果上下文中已有状态ID（从持久化恢复），使用该状态
        val stateId = contextWithState.currentStateId ?: initialState.id
        currentStates[instanceId] = stateId
        contexts[instanceId] = contextWithState.copy(currentStateId = stateId)
        
        return StateMachineInstance(this, instanceId)
    }
    
    /**
     * 恢复已存在的状态机实例（从持久化存储）
     * 用于支持无限长时间停顿后的恢复
     * 即使服务器重启也能恢复实例状态
     */
    fun restore(instanceId: String, context: StateContext) {
        // 如果实例已存在，更新其状态（可能状态已变更）
        val stateId = context.currentStateId ?: flow.states.values.firstOrNull { it.isInitial }?.id
            ?: throw IllegalStateException("No state ID in context and no initial state defined")
        
        currentStates[instanceId] = stateId
        contexts[instanceId] = context
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
        
        // 查找下一个状态
        val nextStateId = findNextState(currentStateId, result)
        
        // 更新上下文（包含状态数据和新的状态ID）
        val updatedLocalState = context.localState + result.data
        val newContext = context.copy(
            currentStateId = nextStateId,
            localState = updatedLocalState
        )
        contexts[instanceId] = newContext
        
        // 更新当前状态映射
        if (nextStateId != null) {
            currentStates[instanceId] = nextStateId
        }
        
        return result
    }
    
    /**
     * 直接转移状态（不触发状态函数，用于超时等特殊情况）
     * 
     * @param instanceId 实例ID
     * @param targetStateId 目标状态ID
     * @param reason 转移原因（用于日志和审计）
     * @throws IllegalStateException 如果实例不存在或目标状态不存在
     */
    fun forceTransition(instanceId: String, targetStateId: String, reason: String = "force") {
        // 验证目标状态存在
        val targetState = flow.states[targetStateId]
            ?: throw IllegalStateException("Target state not found: $targetStateId")
        
        val currentStateId = currentStates[instanceId] 
            ?: throw IllegalStateException("Instance not found: $instanceId")
        val context = contexts[instanceId] 
            ?: throw IllegalStateException("Context not found: $instanceId")
        
        // 直接更新状态
        currentStates[instanceId] = targetStateId
        contexts[instanceId] = context.copy(currentStateId = targetStateId)
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
     * 获取状态定义
     */
    fun getStateDefinition(stateId: String): StateFlow.StateDefinition? = flow.states[stateId]
    
    /**
     * 获取流程配置
     */
    fun getFlow(): StateFlow = flow
    
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
     * 获取关联的状态机
     */
    fun getMachine(): StateMachine = machine
    
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

