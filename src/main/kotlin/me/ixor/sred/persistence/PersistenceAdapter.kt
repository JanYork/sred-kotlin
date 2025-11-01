package me.ixor.sred.persistence

import me.ixor.sred.core.*
import me.ixor.sred.state.StatePersistence
import me.ixor.sred.state.StateHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Instant

/**
 * 持久化适配器配置接口
 * 用于配置不同类型的数据库连接参数
 */
interface PersistenceAdapterConfig {
    /**
     * 数据库类型
     */
    val databaseType: DatabaseType
    
    /**
     * 连接URL或路径
     */
    val connectionString: String
    
    /**
     * 用户名（可选）
     */
    val username: String?
    
    /**
     * 密码（可选）
     */
    val password: String?
    
    /**
     * 其他配置参数
     */
    val properties: Map<String, Any>
}

/**
 * 数据库类型枚举
 */
enum class DatabaseType {
    SQLITE,
    MYSQL,
    POSTGRESQL,
    H2,
    MONGODB,
    REDIS,
    IN_MEMORY
}

/**
 * 抽象持久化适配器基类
 * 
 * 提供通用的序列化/反序列化功能，子类只需实现数据库特定的操作
 */
abstract class AbstractPersistenceAdapter(
    protected val config: PersistenceAdapterConfig
) : StatePersistence {
    
    /**
     * Jackson对象映射器，用于序列化/反序列化
     */
    protected val objectMapper = jacksonObjectMapper()
    
    /**
     * 初始化适配器
     */
    abstract suspend fun initialize()
    
    /**
     * 关闭适配器资源
     */
    abstract fun close()
    
    /**
     * 将对象序列化为JSON字符串
     */
    protected fun serialize(obj: Any): String {
        return objectMapper.writeValueAsString(obj)
    }
    
    /**
     * 将JSON字符串反序列化为Map
     */
    protected inline fun <reified T> deserialize(json: String): T {
        return objectMapper.readValue(json)
    }
    
    /**
     * 构建事件数据Map
     */
    protected fun buildEventData(event: Event): Map<String, Any> {
        return mapOf(
            "description" to event.description,
            "source" to event.source,
            "priority" to event.priority.name,
            "payload" to event.payload,
            "metadata" to event.metadata
        )
    }
    
    /**
     * 从Map中安全地获取String值
     */
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.getString(key: String, default: String = ""): String {
        return (this[key] as? String) ?: default
    }
    
    /**
     * 从Map中安全地获取Map值
     */
    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.getMap(key: String, default: Map<String, Any> = emptyMap()): Map<String, Any> {
        val value = this[key] as? Map<*, *> ?: return default
        return value.mapValues { it.value as Any } as Map<String, Any>
    }
    
    /**
     * 从事件数据Map重建事件
     */
    protected fun buildEventFromData(
        eventId: String,
        eventTypeStr: String,
        eventName: String,
        eventData: Map<String, Any>,
        timestamp: Instant
    ): Event {
        val parts = eventTypeStr.split(":")
        
        return SimpleEvent(
            id = eventId,
            type = EventType(
                name = parts.getOrNull(1) ?: "",
                version = parts.getOrNull(2) ?: "1.0",
                namespace = parts.getOrNull(0) ?: "default"
            ),
            name = eventName,
            description = eventData.getString("description"),
            timestamp = timestamp,
            source = eventData.getString("source", "unknown"),
            priority = EventPriority.values().find { 
                it.name == eventData.getString("priority")
            } ?: EventPriority.NORMAL,
            payload = eventData.getMap("payload"),
            metadata = eventData.getMap("metadata")
        )
    }
    
    /**
     * 从数据库结果构建StateContext（可选，由子类实现）
     */
    protected suspend fun buildStateContext(
        id: String,
        currentStateId: String?,
        createdAt: Instant,
        lastUpdatedAt: Instant,
        localStateJson: String,
        globalStateJson: String,
        metadataJson: String,
        contextId: ContextId
    ): StateContext {
        val localStateMap = deserialize<Map<String, Any>>(localStateJson)
        val globalStateMap = deserialize<Map<String, Any>>(globalStateJson)
        val metadataMap = deserialize<Map<String, Any>>(metadataJson)
        
        // 加载最近的事件（由子类实现具体逻辑）
        val recentEvents = loadRecentEvents(contextId)
        
        return StateContextImpl(
            id = id,
            currentStateId = currentStateId,
            createdAt = createdAt,
            lastUpdatedAt = lastUpdatedAt,
            localState = localStateMap,
            globalState = globalStateMap,
            recentEvents = recentEvents,
            metadata = metadataMap
        )
    }
    
    /**
     * 加载最近的事件历史（由子类实现）
     */
    protected abstract suspend fun loadRecentEvents(contextId: ContextId, limit: Int = 100): List<Event>
}

/**
 * 扩展的状态持久化接口
 * 包含额外的历史记录管理功能
 */
interface ExtendedStatePersistence : StatePersistence {
    /**
     * 保存事件到历史
     */
    suspend fun saveEvent(contextId: ContextId, event: Event)
    
    /**
     * 保存状态历史
     */
    suspend fun saveStateHistory(
        contextId: ContextId,
        fromStateId: StateId?,
        toStateId: StateId,
        eventId: EventId?,
        timestamp: Instant = Instant.now()
    )
    
    /**
     * 获取状态历史
     */
    suspend fun getStateHistory(contextId: ContextId): List<StateHistoryEntry>
}

/**
 * SQLite持久化适配器配置
 */
data class SqlitePersistenceConfig(
    val dbPath: String = "sred_state.db",
    override val properties: Map<String, Any> = emptyMap()
) : PersistenceAdapterConfig {
    override val databaseType: DatabaseType = DatabaseType.SQLITE
    override val connectionString: String = dbPath
    override val username: String? = null
    override val password: String? = null
}

/**
 * MySQL持久化适配器配置
 */
data class MysqlPersistenceConfig(
    override val connectionString: String,
    override val username: String? = null,
    override val password: String? = null,
    val database: String,
    val host: String = "localhost",
    val port: Int = 3306,
    override val properties: Map<String, Any> = emptyMap()
) : PersistenceAdapterConfig {
    override val databaseType: DatabaseType = DatabaseType.MYSQL
}

/**
 * PostgreSQL持久化适配器配置
 */
data class PostgresqlPersistenceConfig(
    override val connectionString: String,
    override val username: String? = null,
    override val password: String? = null,
    val database: String,
    val host: String = "localhost",
    val port: Int = 5432,
    override val properties: Map<String, Any> = emptyMap()
) : PersistenceAdapterConfig {
    override val databaseType: DatabaseType = DatabaseType.POSTGRESQL
}

