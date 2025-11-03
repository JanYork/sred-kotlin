package me.ixor.sred.core

import kotlinx.coroutines.flow.Flow

/**
 * 自治状态接口 - 增强状态自主性
 * 
 * 符合论文要求："状态节点是自治的，能主动注册、响应、更新或退场"
 * "系统的每一部分都在持续监听环境、状态或时间的变化"
 * 
 * 自治状态具备主动感知和轮转的能力，而不仅仅是被动响应事件。
 */
interface AutonomousState : State {
    /**
     * 检查当前状态的自主轮转可能性
     * 
     * 状态可以主动分析上下文，判断是否应该转移到其他状态
     * 这实现了论文中"状态主动轮转"的概念
     * 
     * @param context 当前上下文
     * @return 可能的目标状态列表（按优先级排序）
     */
    suspend fun checkRotationPossibility(context: StateContext): List<StateRotationProposal>
    
    /**
     * 主动感知环境变化
     * 
     * 状态可以持续监听上下文、全局状态或时间的变化
     * 当检测到相关变化时，可以主动提出状态转移建议
     * 
     * @return 环境变化事件流
     */
    fun observeEnvironment(context: StateContext): Flow<EnvironmentChange>
    
    /**
     * 主动提议状态转移
     * 
     * 基于当前上下文和环境变化，主动提出状态转移方案
     * 这体现了状态的"自组织"能力
     * 
     * @param context 当前上下文
     * @param environmentChanges 环境变化列表
     * @return 状态转移提议，如果不需要转移则返回null
     */
    suspend fun proposeTransition(
        context: StateContext,
        environmentChanges: List<EnvironmentChange> = emptyList()
    ): StateTransitionProposal?
    
    /**
     * 获取状态的自治能力级别
     * 
     * @return 自治级别（0-100），越高表示自主性越强
     */
    fun getAutonomyLevel(): Int
    
    /**
     * 状态是否应该主动轮转
     * 
     * 某些状态下，即使没有外部事件，也可能因为上下文变化而需要转移
     * 
     * @param context 当前上下文
     * @return 是否应该主动轮转
     */
    suspend fun shouldAutoRotate(context: StateContext): Boolean
}

/**
 * 状态轮转提议
 * 
 * 表示状态主动提出的转移方案
 */
data class StateRotationProposal(
    /**
     * 目标状态ID
     */
    val targetStateId: StateId,
    
    /**
     * 转移原因（为什么提出这个转移）
     */
    val reason: RotationReason,
    
    /**
     * 置信度（0.0-1.0），表示状态对此次转移的自信程度
     */
    val confidence: Double,
    
    /**
     * 优先级（数值越大优先级越高）
     */
    val priority: Int = 0,
    
    /**
     * 转移的预期收益或必要性说明
     */
    val justification: String = "",
    
    /**
     * 转移所需的上下文变化
     */
    val requiredContextChanges: Map<String, Any> = emptyMap(),
    
    /**
     * 元数据
     */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 轮转原因
 */
enum class RotationReason {
    /**
     * 上下文条件满足：基于局部状态、全局状态的变化
     */
    CONTEXT_CONDITION_MET,
    
    /**
     * 时间触发：基于时间窗口、超时等
     */
    TEMPORAL_TRIGGER,
    
    /**
     * 环境变化：全局系统状态变化
     */
    ENVIRONMENT_CHANGE,
    
    /**
     * 策略驱动：基于策略引擎的决策
     */
    POLICY_DRIVEN,
    
    /**
     * 优化建议：系统优化建议的转移
     */
    OPTIMIZATION_SUGGESTION,
    
    /**
     * 自学习：基于历史数据的预测
     */
    SELF_LEARNING,
    
    /**
     * 用户自定义原因
     */
    CUSTOM
}

/**
 * 状态转移提议
 * 
 * 更完整的转移方案，包含执行计划
 */
data class StateTransitionProposal(
    /**
     * 提议ID
     */
    val proposalId: String,
    
    /**
     * 源状态ID
     */
    val fromStateId: StateId,
    
    /**
     * 目标状态ID
     */
    val targetStateId: StateId,
    
    /**
     * 触发事件（可选，如果是主动转移可能没有事件）
     */
    val triggerEvent: Event?,
    
    /**
     * 转移原因
     */
    val reason: RotationReason,
    
    /**
     * 置信度
     */
    val confidence: Double,
    
    /**
     * 优先级
     */
    val priority: Int,
    
    /**
     * 执行此转移所需的上下文变化
     */
    val contextUpdate: suspend (StateContext) -> StateContext,
    
    /**
     * 转移前检查
     */
    val preTransitionCheck: suspend (StateContext) -> Boolean = { true },
    
    /**
     * 转移后动作
     */
    val postTransitionAction: suspend (StateContext) -> StateContext = { it },
    
    /**
     * 元数据
     */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 环境变化
 * 
 * 表示状态观察到的环境变化
 */
data class EnvironmentChange(
    /**
     * 变化类型
     */
    val type: ChangeType,
    
    /**
     * 变化的键（如局部状态键、全局状态键等）
     */
    val key: String,
    
    /**
     * 旧值
     */
    val oldValue: Any?,
    
    /**
     * 新值
     */
    val newValue: Any?,
    
    /**
     * 变化时间戳
     */
    val timestamp: java.time.Instant,
    
    /**
     * 变化来源（localState, globalState, recentEvents, metadata）
     */
    val source: ContextSource,
    
    /**
     * 变化强度（0.0-1.0）
     */
    val intensity: Double = 1.0,
    
    /**
     * 元数据
     */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 变化类型
 */
enum class ChangeType {
    /**
     * 值被添加
     */
    ADDED,
    
    /**
     * 值被修改
     */
    MODIFIED,
    
    /**
     * 值被删除
     */
    REMOVED,
    
    /**
     * 值超出阈值
     */
    THRESHOLD_EXCEEDED,
    
    /**
     * 值低于阈值
     */
    THRESHOLD_UNDERRUN,
    
    /**
     * 模式匹配（如事件模式、状态模式）
     */
    PATTERN_MATCHED
}

/**
 * 上下文来源
 */
enum class ContextSource {
    LOCAL_STATE,
    GLOBAL_STATE,
    RECENT_EVENTS,
    METADATA
}

/**
 * 自治状态抽象基类
 * 
 * 提供默认实现，子类可以覆盖特定方法
 */
abstract class AbstractAutonomousState(
    id: StateId,
    name: String,
    description: String,
    /**
     * 自治级别（0-100）
     */
    private val autonomyLevel: Int = 50
) : AbstractState(id, name, description), AutonomousState {
    
    override suspend fun checkRotationPossibility(context: StateContext): List<StateRotationProposal> {
        // 默认实现：返回空列表，表示不主动轮转
        // 子类应该覆盖此方法来实现自主轮转逻辑
        return emptyList()
    }
    
    override fun observeEnvironment(context: StateContext): kotlinx.coroutines.flow.Flow<EnvironmentChange> {
        // 默认实现：返回空流
        // 子类应该覆盖此方法来实现环境监听
        return kotlinx.coroutines.flow.flowOf()
    }
    
    override suspend fun proposeTransition(
        context: StateContext,
        environmentChanges: List<EnvironmentChange>
    ): StateTransitionProposal? {
        // 默认实现：不主动提议转移
        // 子类应该覆盖此方法
        return null
    }
    
    override fun getAutonomyLevel(): Int = autonomyLevel
    
    override suspend fun shouldAutoRotate(context: StateContext): Boolean {
        // 默认实现：不主动轮转
        // 子类可以覆盖此方法
        return false
    }
    
    /**
     * 辅助方法：检查局部状态变化
     */
    protected fun detectLocalStateChanges(
        oldContext: StateContext,
        newContext: StateContext
    ): List<EnvironmentChange> {
        val changes = mutableListOf<EnvironmentChange>()
        
        // 检测新增和修改
        newContext.localState.forEach { (key, newValue) ->
            val oldValue = oldContext.localState[key]
            when {
                oldValue == null -> changes.add(
                    EnvironmentChange(
                        type = ChangeType.ADDED,
                        key = key,
                        oldValue = null,
                        newValue = newValue,
                        timestamp = java.time.Instant.now(),
                        source = ContextSource.LOCAL_STATE
                    )
                )
                oldValue != newValue -> changes.add(
                    EnvironmentChange(
                        type = ChangeType.MODIFIED,
                        key = key,
                        oldValue = oldValue,
                        newValue = newValue,
                        timestamp = java.time.Instant.now(),
                        source = ContextSource.LOCAL_STATE,
                        intensity = calculateChangeIntensity(oldValue, newValue)
                    )
                )
            }
        }
        
        // 检测删除
        oldContext.localState.forEach { (key, _) ->
            if (!newContext.localState.containsKey(key)) {
                changes.add(
                    EnvironmentChange(
                        type = ChangeType.REMOVED,
                        key = key,
                        oldValue = oldContext.localState[key],
                        newValue = null,
                        timestamp = java.time.Instant.now(),
                        source = ContextSource.LOCAL_STATE
                    )
                )
            }
        }
        
        return changes
    }
    
    /**
     * 辅助方法：检查全局状态变化
     */
    protected fun detectGlobalStateChanges(
        oldContext: StateContext,
        newContext: StateContext
    ): List<EnvironmentChange> {
        // 类似局部状态的检测逻辑
        val changes = mutableListOf<EnvironmentChange>()
        
        newContext.globalState.forEach { (key, newValue) ->
            val oldValue = oldContext.globalState[key]
            when {
                oldValue == null -> changes.add(
                    EnvironmentChange(
                        type = ChangeType.ADDED,
                        key = key,
                        oldValue = null,
                        newValue = newValue,
                        timestamp = java.time.Instant.now(),
                        source = ContextSource.GLOBAL_STATE
                    )
                )
                oldValue != newValue -> changes.add(
                    EnvironmentChange(
                        type = ChangeType.MODIFIED,
                        key = key,
                        oldValue = oldValue,
                        newValue = newValue,
                        timestamp = java.time.Instant.now(),
                        source = ContextSource.GLOBAL_STATE,
                        intensity = calculateChangeIntensity(oldValue, newValue)
                    )
                )
            }
        }
        
        return changes
    }
    
    /**
     * 计算变化强度
     */
    private fun calculateChangeIntensity(oldValue: Any?, newValue: Any?): Double {
        // 简单的变化强度计算
        // 可以根据值类型进行更复杂的计算
        return when {
            oldValue == null || newValue == null -> 1.0
            oldValue == newValue -> 0.0
            oldValue is Number && newValue is Number -> {
                val oldNum = oldValue.toDouble()
                val newNum = newValue.toDouble()
                if (oldNum == 0.0) 1.0
                else kotlin.math.abs((newNum - oldNum) / oldNum).coerceIn(0.0, 1.0)
            }
            else -> 0.5 // 默认中等强度
        }
    }
}



