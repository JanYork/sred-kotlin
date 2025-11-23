package me.ixor.sred.core

import java.time.Instant
import java.util.*

/**
 * 状态接口 - SRED架构的核心抽象
 * 
 * 状态代表"系统此刻的存在方式"，是系统语义的基本单位。
 * 每个状态节点封装一类业务行为，具备自治的生命周期。
 */
interface State {
    /**
     * 状态唯一标识符
     */
    val id: StateId
    
    /**
     * 状态名称，用于人类可读的标识
     */
    val name: String
    
    /**
     * 状态描述
     */
    val description: String
    
    /**
     * 状态进入条件
     * @param context 当前上下文
     * @return 是否可以进入此状态
     */
    fun canEnter(context: StateContext): Boolean
    
    /**
     * 状态进入时的行为
     * @param context 当前上下文
     * @return 进入后的新上下文
     */
    suspend fun onEnter(context: StateContext): StateContext
    
    /**
     * 状态退出时的行为
     * @param context 当前上下文
     * @return 退出后的新上下文
     */
    suspend fun onExit(context: StateContext): StateContext
    
    /**
     * 状态响应事件的能力
     * @param event 事件
     * @param context 当前上下文
     * @return 是否可以响应此事件
     */
    fun canHandle(event: Event, context: StateContext): Boolean
    
    /**
     * 处理事件
     * @param event 事件
     * @param context 当前上下文
     * @return 处理结果，包含可能的状态转移
     */
    suspend fun handleEvent(event: Event, context: StateContext): StateTransitionResult
    
    /**
     * 获取可能转移到的目标状态集合
     * @param context 当前上下文
     * @return 目标状态集合
     */
    fun getPossibleTransitions(context: StateContext): Set<StateId>
}

/**
 * 状态ID类型别名
 */
typealias StateId = String

/**
 * 状态转移结果
 */
data class StateTransitionResult(
    val success: Boolean,
    val nextStateId: StateId? = null,
    val updatedContext: StateContext,
    val error: Throwable? = null,
    val sideEffects: List<SideEffect> = emptyList()
)

/**
 * 副作用接口 - 状态转移时可能产生的副作用
 */
interface SideEffect {
    val id: String
    val description: String
    suspend fun execute(context: StateContext): SideEffectResult
}

/**
 * 副作用执行结果
 */
data class SideEffectResult(
    val success: Boolean,
    val error: Throwable? = null,
    val data: Map<String, Any> = emptyMap()
)

/**
 * 抽象状态基类 - 提供默认实现
 */
abstract class AbstractState(
    override val id: StateId,
    override val name: String,
    override val description: String
) : State {
    
    override fun canEnter(context: StateContext): Boolean = true
    
    override suspend fun onEnter(context: StateContext): StateContext = context
    
    override suspend fun onExit(context: StateContext): StateContext = context
    
    override fun canHandle(event: Event, context: StateContext): Boolean = false
    
    override suspend fun handleEvent(event: Event, context: StateContext): StateTransitionResult {
        return StateTransitionResult(
            success = false,
            updatedContext = context,
            error = UnsupportedOperationException("State $id cannot handle event ${event.id}")
        )
    }
    
    override fun getPossibleTransitions(context: StateContext): Set<StateId> = emptySet()
}
