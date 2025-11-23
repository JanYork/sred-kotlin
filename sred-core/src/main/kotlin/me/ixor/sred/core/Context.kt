package me.ixor.sred.core

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 状态上下文 - SRED架构的上下文驱动核心
 * 
 * 上下文C = (Σ_local, Σ_global, E_recent, M)
 * 其中：
 * - Σ_local: 当前实例的局部状态
 * - Σ_global: 系统全局状态（或全局约束）
 * - E_recent: 最近接收到的事件
 * - M: 可用的元信息（如版本、策略、依赖关系）
 */
interface StateContext {
    /**
     * 上下文唯一标识符
     */
    val id: ContextId
    
    /**
     * 当前状态ID
     */
    val currentStateId: StateId?
    
    /**
     * 上下文创建时间
     */
    val createdAt: Instant
    
    /**
     * 最后更新时间
     */
    val lastUpdatedAt: Instant
    
    /**
     * 局部状态数据
     */
    val localState: Map<String, Any>
    
    /**
     * 全局状态数据
     */
    val globalState: Map<String, Any>
    
    /**
     * 最近的事件历史
     */
    val recentEvents: List<Event>
    
    /**
     * 元信息
     */
    val metadata: Map<String, Any>
    
    /**
     * 获取局部状态值
     */
    fun <T> getLocalState(key: String, type: Class<T>): T?
    
    /**
     * 获取全局状态值
     */
    fun <T> getGlobalState(key: String, type: Class<T>): T?
    
    /**
     * 获取元信息值
     */
    fun <T> getMetadata(key: String, type: Class<T>): T?
    
    /**
     * 更新局部状态
     */
    fun updateLocalState(key: String, value: Any): StateContext
    
    /**
     * 更新全局状态
     */
    fun updateGlobalState(key: String, value: Any): StateContext
    
    /**
     * 更新元信息
     */
    fun updateMetadata(key: String, value: Any): StateContext
    
    /**
     * 添加事件到历史
     */
    fun addEvent(event: Event): StateContext
    
    /**
     * 创建新的上下文副本
     */
    fun copy(
        currentStateId: StateId? = this.currentStateId,
        localState: Map<String, Any> = this.localState,
        globalState: Map<String, Any> = this.globalState,
        recentEvents: List<Event> = this.recentEvents,
        metadata: Map<String, Any> = this.metadata
    ): StateContext
}

/**
 * 上下文ID类型别名
 */
typealias ContextId = String

/**
 * 状态上下文实现
 */
data class StateContextImpl(
    override val id: ContextId,
    override val currentStateId: StateId?,
    override val createdAt: Instant,
    override val lastUpdatedAt: Instant,
    override val localState: Map<String, Any>,
    override val globalState: Map<String, Any>,
    override val recentEvents: List<Event>,
    override val metadata: Map<String, Any>
) : StateContext {
    
    override fun <T> getLocalState(key: String, type: Class<T>): T? {
        return localState[key]?.let { value ->
            if (type.isInstance(value)) {
                @Suppress("UNCHECKED_CAST")
                value as T
            } else {
                null
            }
        }
    }
    
    override fun <T> getGlobalState(key: String, type: Class<T>): T? {
        return globalState[key]?.let { value ->
            if (type.isInstance(value)) {
                @Suppress("UNCHECKED_CAST")
                value as T
            } else {
                null
            }
        }
    }
    
    override fun <T> getMetadata(key: String, type: Class<T>): T? {
        return metadata[key]?.let { value ->
            if (type.isInstance(value)) {
                @Suppress("UNCHECKED_CAST")
                value as T
            } else {
                null
            }
        }
    }
    
    override fun updateLocalState(key: String, value: Any): StateContext {
        val newLocalState = localState.toMutableMap().apply { put(key, value) }
        return copy(
            localState = newLocalState,
            lastUpdatedAt = Instant.now()
        )
    }
    
    override fun updateGlobalState(key: String, value: Any): StateContext {
        val newGlobalState = globalState.toMutableMap().apply { put(key, value) }
        return copy(
            globalState = newGlobalState,
            lastUpdatedAt = Instant.now()
        )
    }
    
    override fun updateMetadata(key: String, value: Any): StateContext {
        val newMetadata = metadata.toMutableMap().apply { put(key, value) }
        return copy(
            metadata = newMetadata,
            lastUpdatedAt = Instant.now()
        )
    }
    
    override fun addEvent(event: Event): StateContext {
        val newEvents = (recentEvents + event).takeLast(100) // 保持最近100个事件
        return copy(
            recentEvents = newEvents,
            lastUpdatedAt = Instant.now()
        )
    }
    
    override fun copy(
        currentStateId: StateId?,
        localState: Map<String, Any>,
        globalState: Map<String, Any>,
        recentEvents: List<Event>,
        metadata: Map<String, Any>
    ): StateContext {
        return StateContextImpl(
            id = this.id,
            currentStateId = currentStateId,
            createdAt = this.createdAt,
            lastUpdatedAt = Instant.now(),
            localState = localState,
            globalState = globalState,
            recentEvents = recentEvents,
            metadata = metadata
        )
    }
}

/**
 * 上下文构建器
 */
class StateContextBuilder {
    private var id: ContextId = UUID.randomUUID().toString()
    private var currentStateId: StateId? = null
    private var createdAt: Instant = Instant.now()
    private var localState: MutableMap<String, Any> = mutableMapOf()
    private var globalState: MutableMap<String, Any> = mutableMapOf()
    private var recentEvents: MutableList<Event> = mutableListOf()
    private var metadata: MutableMap<String, Any> = mutableMapOf()
    
    fun id(id: ContextId) = apply { this.id = id }
    fun currentStateId(currentStateId: StateId?) = apply { this.currentStateId = currentStateId }
    fun createdAt(createdAt: Instant) = apply { this.createdAt = createdAt }
    fun localState(key: String, value: Any) = apply { this.localState[key] = value }
    fun localState(map: Map<String, Any>) = apply { this.localState.putAll(map) }
    fun globalState(key: String, value: Any) = apply { this.globalState[key] = value }
    fun globalState(map: Map<String, Any>) = apply { this.globalState.putAll(map) }
    fun addEvent(event: Event) = apply { this.recentEvents.add(event) }
    fun addEvents(events: Collection<Event>) = apply { this.recentEvents.addAll(events) }
    fun metadata(key: String, value: Any) = apply { this.metadata[key] = value }
    fun metadata(map: Map<String, Any>) = apply { this.metadata.putAll(map) }
    
    fun build(): StateContext {
        return StateContextImpl(
            id = id,
            currentStateId = currentStateId,
            createdAt = createdAt,
            lastUpdatedAt = Instant.now(),
            localState = localState.toMap(),
            globalState = globalState.toMap(),
            recentEvents = recentEvents.toList(),
            metadata = metadata.toMap()
        )
    }
}

/**
 * 上下文工厂
 */
object StateContextFactory {
    fun builder(): StateContextBuilder = StateContextBuilder()
    
    fun create(
        currentStateId: StateId? = null,
        localState: Map<String, Any> = emptyMap(),
        globalState: Map<String, Any> = emptyMap(),
        metadata: Map<String, Any> = emptyMap()
    ): StateContext = builder()
        .currentStateId(currentStateId)
        .apply { localState.forEach { (k, v) -> localState(k, v) } }
        .apply { globalState.forEach { (k, v) -> globalState(k, v) } }
        .apply { metadata.forEach { (k, v) -> metadata(k, v) } }
        .build()
}
