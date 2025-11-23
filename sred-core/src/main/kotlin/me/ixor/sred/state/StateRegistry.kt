package me.ixor.sred.state

import me.ixor.sred.core.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 状态注册表 - SRED架构的状态层核心
 * 
 * 状态注册机制允许函数或服务主动声明自己的状态归属，
 * 形成"自注册的状态生态"。
 * 
 * 注册机制的核心思想：
 * 服务不是被调用的对象，而是主动向状态管理中心声明"我负责这个状态"。
 */
interface StateRegistry {
    /**
     * 注册状态
     */
    suspend fun registerState(state: State)
    
    /**
     * 注销状态
     */
    suspend fun unregisterState(stateId: StateId)
    
    /**
     * 获取状态
     */
    fun getState(stateId: StateId): State?
    
    /**
     * 获取所有状态
     */
    fun getAllStates(): Collection<State>
    
    /**
     * 检查状态是否存在
     */
    fun hasState(stateId: StateId): Boolean
    
    /**
     * 获取状态统计信息
     */
    fun getStatistics(): StateRegistryStatistics
    
    /**
     * 清空所有状态
     */
    suspend fun clear()
}

/**
 * 状态注册表统计信息
 */
data class StateRegistryStatistics(
    val totalStates: Int,
    val activeStates: Int,
    val registeredStates: Set<StateId>
)

/**
 * 状态注册表实现
 */
class StateRegistryImpl : StateRegistry {
    private val states = ConcurrentHashMap<StateId, State>()
    private val mutex = Mutex()
    
    override suspend fun registerState(state: State) {
        mutex.withLock {
            states[state.id] = state
        }
    }
    
    override suspend fun unregisterState(stateId: StateId) {
        mutex.withLock {
            states.remove(stateId)
        }
    }
    
    override fun getState(stateId: StateId): State? = states[stateId]
    
    override fun getAllStates(): Collection<State> = states.values
    
    override fun hasState(stateId: StateId): Boolean = states.containsKey(stateId)
    
    override fun getStatistics(): StateRegistryStatistics {
        return StateRegistryStatistics(
            totalStates = states.size,
            activeStates = states.size,
            registeredStates = states.keys.toSet()
        )
    }
    
    override suspend fun clear() {
        mutex.withLock {
            states.clear()
        }
    }
}

/**
 * 状态注册表工厂
 */
object StateRegistryFactory {
    fun create(): StateRegistry = StateRegistryImpl()
}
