package me.ixor.sred.core

import java.time.Instant
import java.util.*

/**
 * 事件接口 - SRED架构的动力层
 * 
 * 事件代表"系统外部或内部发生的变化"，是状态变化的"原因"。
 * 事件驱动系统以变化为核心驱动力，系统被动响应外部刺激。
 */
interface Event {
    /**
     * 事件唯一标识符
     */
    val id: EventId
    
    /**
     * 事件类型
     */
    val type: EventType
    
    /**
     * 事件名称
     */
    val name: String
    
    /**
     * 事件描述
     */
    val description: String
    
    /**
     * 事件发生时间
     */
    val timestamp: Instant
    
    /**
     * 事件源标识
     */
    val source: String
    
    /**
     * 事件优先级
     */
    val priority: EventPriority
    
    /**
     * 事件数据载荷
     */
    val payload: Map<String, Any>
    
    /**
     * 事件元数据
     */
    val metadata: Map<String, Any>
    
    /**
     * 事件是否已过期
     */
    fun isExpired(currentTime: Instant = Instant.now()): Boolean
    
    /**
     * 事件是否可以被重放
     */
    fun isReplayable(): Boolean
}

/**
 * 事件ID类型别名
 */
typealias EventId = String

/**
 * 事件类型
 */
data class EventType(
    val name: String,
    val version: String = "1.0",
    val namespace: String = "default"
) {
    override fun toString(): String = "$namespace:$name:$version"
}

/**
 * 事件优先级
 */
enum class EventPriority(val level: Int) {
    LOW(1),
    NORMAL(2),
    HIGH(3),
    CRITICAL(4)
}

/**
 * 抽象事件基类
 */
abstract class AbstractEvent(
    override val id: EventId = UUID.randomUUID().toString(),
    override val type: EventType,
    override val name: String,
    override val description: String,
    override val timestamp: Instant = Instant.now(),
    override val source: String = "unknown",
    override val priority: EventPriority = EventPriority.NORMAL,
    override val payload: Map<String, Any> = emptyMap(),
    override val metadata: Map<String, Any> = emptyMap()
) : Event {
    
    override fun isExpired(currentTime: Instant): Boolean = false
    
    override fun isReplayable(): Boolean = true
    
    override fun toString(): String = "Event($type, $name, $timestamp)"
}

/**
 * 事件构建器
 */
class EventBuilder {
    private var id: EventId = UUID.randomUUID().toString()
    private var type: EventType? = null
    private var name: String = ""
    private var description: String = ""
    private var timestamp: Instant = Instant.now()
    private var source: String = "unknown"
    private var priority: EventPriority = EventPriority.NORMAL
    private var payload: MutableMap<String, Any> = mutableMapOf()
    private var metadata: MutableMap<String, Any> = mutableMapOf()
    
    fun id(id: EventId) = apply { this.id = id }
    fun type(type: EventType) = apply { this.type = type }
    fun type(name: String, version: String = "1.0", namespace: String = "default") = 
        apply { this.type = EventType(name, version, namespace) }
    fun name(name: String) = apply { this.name = name }
    fun description(description: String) = apply { this.description = description }
    fun timestamp(timestamp: Instant) = apply { this.timestamp = timestamp }
    fun source(source: String) = apply { this.source = source }
    fun priority(priority: EventPriority) = apply { this.priority = priority }
    fun payload(key: String, value: Any) = apply { this.payload[key] = value }
    fun payload(map: Map<String, Any>) = apply { this.payload.putAll(map) }
    fun metadata(key: String, value: Any) = apply { this.metadata[key] = value }
    fun metadata(map: Map<String, Any>) = apply { this.metadata.putAll(map) }
    
    fun build(): Event {
        require(type != null) { "Event type is required" }
        return SimpleEvent(
            id = id,
            type = type!!,
            name = name,
            description = description,
            timestamp = timestamp,
            source = source,
            priority = priority,
            payload = payload.toMap(),
            metadata = metadata.toMap()
        )
    }
}

/**
 * 简单事件实现
 */
data class SimpleEvent(
    override val id: EventId,
    override val type: EventType,
    override val name: String,
    override val description: String,
    override val timestamp: Instant,
    override val source: String,
    override val priority: EventPriority,
    override val payload: Map<String, Any>,
    override val metadata: Map<String, Any>
) : Event {
    
    override fun isExpired(currentTime: Instant): Boolean = false
    
    override fun isReplayable(): Boolean = true
}

/**
 * 事件工厂
 */
object EventFactory {
    fun builder(): EventBuilder = EventBuilder()
    
    fun create(
        type: EventType,
        name: String,
        description: String = "",
        source: String = "system",
        priority: EventPriority = EventPriority.NORMAL,
        payload: Map<String, Any> = emptyMap()
    ): Event = builder()
        .type(type)
        .name(name)
        .description(description)
        .source(source)
        .priority(priority)
        .apply { payload.forEach { (k, v) -> payload(k, v) } }
        .build()
    
    /**
     * 创建简单事件（使用事件名称作为类型）
     */
    fun createSimpleEvent(
        eventName: String,
        payload: Map<String, Any> = emptyMap()
    ): Event = create(
        type = EventType("system", eventName),
        name = eventName,
        description = "",
        source = "system",
        priority = EventPriority.NORMAL,
        payload = payload
    )
}
