package me.ixor.sred.core

import java.time.Instant
import java.util.*

/**
 * 状态转移函数 - SRED架构的核心语义
 * 
 * 状态转移函数 T: Σ × E → Σ
 * 其中 Σ 是状态集合，E 是事件集合
 * 
 * 在SRED模型中，状态转移不仅依赖于当前状态与事件，
 * 还受上下文环境影响：s' = T(s, e, C)
 */
interface StateTransition {
    /**
     * 转移函数标识符
     */
    val id: TransitionId
    
    /**
     * 转移名称
     */
    val name: String
    
    /**
     * 源状态ID
     */
    val fromStateId: StateId
    
    /**
     * 目标状态ID
     */
    val toStateId: StateId
    
    /**
     * 触发事件类型
     */
    val eventType: EventType
    
    /**
     * 转移条件
     */
    val condition: TransitionCondition
    
    /**
     * 转移动作
     */
    val action: TransitionAction
    
    /**
     * 转移优先级
     */
    val priority: Int
    
    /**
     * 是否启用
     */
    val enabled: Boolean
    
    /**
     * 检查是否可以执行转移
     */
    fun canTransition(
        currentState: State,
        event: Event,
        context: StateContext
    ): Boolean
    
    /**
     * 执行状态转移
     */
    suspend fun executeTransition(
        currentState: State,
        event: Event,
        context: StateContext
    ): StateTransitionResult
}

/**
 * 转移ID类型别名
 */
typealias TransitionId = String

/**
 * 转移条件接口
 */
interface TransitionCondition {
    /**
     * 检查条件是否满足
     */
    fun evaluate(
        currentState: State,
        event: Event,
        context: StateContext
    ): Boolean
}

/**
 * 转移动作接口
 */
interface TransitionAction {
    /**
     * 执行转移动作
     */
    suspend fun execute(
        currentState: State,
        event: Event,
        context: StateContext
    ): StateTransitionResult
}

/**
 * 抽象状态转移实现
 */
abstract class AbstractStateTransition(
    override val id: TransitionId,
    override val name: String,
    override val fromStateId: StateId,
    override val toStateId: StateId,
    override val eventType: EventType,
    override val condition: TransitionCondition,
    override val action: TransitionAction,
    override val priority: Int = 0,
    override val enabled: Boolean = true
) : StateTransition {
    
    override fun canTransition(
        currentState: State,
        event: Event,
        context: StateContext
    ): Boolean {
        if (!enabled) return false
        if (currentState.id != fromStateId) return false
        if (event.type != eventType) return false
        return condition.evaluate(currentState, event, context)
    }
    
    override suspend fun executeTransition(
        currentState: State,
        event: Event,
        context: StateContext
    ): StateTransitionResult {
        return action.execute(currentState, event, context)
    }
}

/**
 * 简单转移条件实现
 */
class SimpleTransitionCondition(
    private val predicate: (State, Event, StateContext) -> Boolean
) : TransitionCondition {
    override fun evaluate(
        currentState: State,
        event: Event,
        context: StateContext
    ): Boolean = predicate(currentState, event, context)
}

/**
 * 简单转移动作实现
 */
class SimpleTransitionAction(
    private val action: suspend (State, Event, StateContext) -> StateTransitionResult
) : TransitionAction {
    override suspend fun execute(
        currentState: State,
        event: Event,
        context: StateContext
    ): StateTransitionResult = action(currentState, event, context)
}

/**
 * 状态转移构建器
 */
class StateTransitionBuilder {
    private var id: TransitionId = UUID.randomUUID().toString()
    private var name: String = ""
    private var fromStateId: StateId = ""
    private var toStateId: StateId = ""
    private var eventType: EventType? = null
    private var condition: TransitionCondition? = null
    private var action: TransitionAction? = null
    private var priority: Int = 0
    private var enabled: Boolean = true
    
    fun id(id: TransitionId) = apply { this.id = id }
    fun name(name: String) = apply { this.name = name }
    fun fromStateId(fromStateId: StateId) = apply { this.fromStateId = fromStateId }
    fun toStateId(toStateId: StateId) = apply { this.toStateId = toStateId }
    fun eventType(eventType: EventType) = apply { this.eventType = eventType }
    fun condition(condition: TransitionCondition) = apply { this.condition = condition }
    fun action(action: TransitionAction) = apply { this.action = action }
    fun priority(priority: Int) = apply { this.priority = priority }
    fun enabled(enabled: Boolean) = apply { this.enabled = enabled }
    
    fun build(): StateTransition {
        require(eventType != null) { "Event type is required" }
        require(condition != null) { "Condition is required" }
        require(action != null) { "Action is required" }
        
        return object : StateTransition {
            override val id: TransitionId = this@StateTransitionBuilder.id
            override val name: String = this@StateTransitionBuilder.name
            override val fromStateId: StateId = this@StateTransitionBuilder.fromStateId
            override val toStateId: StateId = this@StateTransitionBuilder.toStateId
            override val eventType: EventType = this@StateTransitionBuilder.eventType!!
            override val condition: TransitionCondition = this@StateTransitionBuilder.condition!!
            override val action: TransitionAction = this@StateTransitionBuilder.action!!
            override val priority: Int = this@StateTransitionBuilder.priority
            override val enabled: Boolean = this@StateTransitionBuilder.enabled
            
            override fun canTransition(
                currentState: State,
                event: Event,
                context: StateContext
            ): Boolean {
                if (!enabled) return false
                if (currentState.id != fromStateId) return false
                if (event.type != eventType) return false
                return condition.evaluate(currentState, event, context)
            }
            
            override suspend fun executeTransition(
                currentState: State,
                event: Event,
                context: StateContext
            ): StateTransitionResult = action.execute(currentState, event, context)
        }
    }
}

/**
 * 状态转移工厂
 */
object StateTransitionFactory {
    fun builder(): StateTransitionBuilder = StateTransitionBuilder()
    
    fun createSimpleTransition(
        fromStateId: StateId,
        toStateId: StateId,
        eventType: EventType,
        condition: (State, Event, StateContext) -> Boolean = { _, _, _ -> true },
        action: suspend (State, Event, StateContext) -> StateTransitionResult
    ): StateTransition = builder()
        .fromStateId(fromStateId)
        .toStateId(toStateId)
        .eventType(eventType)
        .condition(SimpleTransitionCondition(condition))
        .action(SimpleTransitionAction(action))
        .build()
}
