package me.ixor.sred.persistence

import me.ixor.sred.core.*
import me.ixor.sred.state.StatePersistence
import me.ixor.sred.state.StateHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Instant
import java.io.Serializable
import kotlin.coroutines.coroutineContext

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
     * @param contextId 上下文ID
     * @param event 事件
     * @param transactionId 可选的事务ID，如果提供则在同一事务中执行
     */
    suspend fun saveEvent(contextId: ContextId, event: Event, transactionId: TransactionId? = null)
    
    /**
     * 保存状态历史
     * @param contextId 上下文ID
     * @param fromStateId 源状态ID
     * @param toStateId 目标状态ID
     * @param eventId 事件ID
     * @param timestamp 时间戳
     * @param transactionId 可选的事务ID，如果提供则在同一事务中执行
     */
    suspend fun saveStateHistory(
        contextId: ContextId,
        fromStateId: StateId?,
        toStateId: StateId,
        eventId: EventId?,
        timestamp: Instant = Instant.now(),
        transactionId: TransactionId? = null
    )
    
    /**
     * 获取状态历史
     * @param contextId 上下文ID
     * @param transactionId 可选的事务ID，如果提供则在同一事务中执行
     */
    suspend fun getStateHistory(contextId: ContextId, transactionId: TransactionId? = null): List<StateHistoryEntry>
    
    /**
     * 查询所有暂停的实例ID
     * 返回所有 metadata 中包含 _pausedAt 标记的实例ID列表
     * @param transactionId 可选的事务ID，如果提供则在同一事务中执行
     */
    suspend fun findPausedInstances(transactionId: TransactionId? = null): List<ContextId>
    
    /**
     * 创建状态快照
     * @param contextId 上下文ID
     * @param snapshotId 快照ID（可选，如果为null则自动生成）
     * @param description 快照描述
     * @param transactionId 可选的事务ID
     * @return 快照ID
     */
    suspend fun createSnapshot(
        contextId: ContextId,
        snapshotId: String? = null,
        description: String? = null,
        transactionId: TransactionId? = null
    ): String
    
    /**
     * 列出所有快照
     * @param contextId 上下文ID
     * @param transactionId 可选的事务ID
     * @return 快照列表
     */
    suspend fun listSnapshots(
        contextId: ContextId,
        transactionId: TransactionId? = null
    ): List<StateSnapshot>
    
    /**
     * 加载指定快照
     * @param contextId 上下文ID
     * @param snapshotId 快照ID
     * @param transactionId 可选的事务ID
     * @return 快照状态上下文，如果不存在则返回null
     */
    suspend fun loadSnapshot(
        contextId: ContextId,
        snapshotId: String,
        transactionId: TransactionId? = null
    ): StateContext?
    
    /**
     * 加载指定时间点的快照
     * @param contextId 上下文ID
     * @param timestamp 时间点
     * @param transactionId 可选的事务ID
     * @return 最接近该时间点的快照
     */
    suspend fun loadSnapshotByTime(
        contextId: ContextId,
        timestamp: Instant,
        transactionId: TransactionId? = null
    ): StateSnapshot?
    
    /**
     * 回滚到指定快照
     * @param contextId 上下文ID
     * @param snapshotId 快照ID
     * @param transactionId 可选的事务ID
     * @return 是否成功
     */
    suspend fun rollbackToSnapshot(
        contextId: ContextId,
        snapshotId: String,
        transactionId: TransactionId? = null
    ): Boolean
    
    /**
     * 删除快照
     * @param contextId 上下文ID
     * @param snapshotId 快照ID
     * @param transactionId 可选的事务ID
     * @return 是否成功
     */
    suspend fun deleteSnapshot(
        contextId: ContextId,
        snapshotId: String,
        transactionId: TransactionId? = null
    ): Boolean
    
    /**
     * 验证状态上下文一致性
     * @param context 状态上下文
     * @return 验证结果
     */
    suspend fun validateContext(context: StateContext): ContextValidationResult
    
    /**
     * 修复状态上下文的不一致性
     * @param contextId 上下文ID
     * @param issues 需要修复的问题列表
     * @param transactionId 可选的事务ID
     * @return 修复后的上下文
     */
    suspend fun repairContext(
        contextId: ContextId,
        issues: List<ContextValidationIssue>,
        transactionId: TransactionId? = null
    ): StateContext?
    
    /**
     * 导出状态上下文（用于跨实例迁移）
     * @param contextId 上下文ID
     * @param transactionId 可选的事务ID
     * @return 导出的上下文数据
     */
    suspend fun exportContext(
        contextId: ContextId,
        transactionId: TransactionId? = null
    ): ExportedContext
    
    /**
     * 导入状态上下文（用于跨实例迁移）
     * @param exportedContext 导出的上下文数据
     * @param targetContextId 目标上下文ID（可选，如果为null则使用原ID）
     * @param transactionId 可选的事务ID
     * @return 导入后的上下文ID
     */
    suspend fun importContext(
        exportedContext: ExportedContext,
        targetContextId: ContextId? = null,
        transactionId: TransactionId? = null
    ): ContextId
}

/**
 * 导出的上下文数据
 */
data class ExportedContext(
    val contextId: ContextId,
    val context: StateContext,
    val history: List<StateHistoryEntry> = emptyList(),
    val snapshots: List<StateSnapshot> = emptyList(),
    val metadata: Map<String, Any> = emptyMap(),
    val exportedAt: Instant = Instant.now(),
    val sourceInstance: String? = null,
    val version: String = "1.0"
)

/**
 * 状态快照
 */
data class StateSnapshot(
    val snapshotId: String,
    val contextId: ContextId,
    val timestamp: Instant,
    val description: String? = null,
    val context: StateContext,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 上下文验证结果
 */
data class ContextValidationResult(
    val isValid: Boolean,
    val issues: List<ContextValidationIssue> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * 上下文验证问题
 */
data class ContextValidationIssue(
    val type: ValidationIssueType,
    val severity: ValidationSeverity,
    val message: String,
    val field: String? = null,
    val suggestedFix: String? = null
)

/**
 * 验证问题类型
 */
enum class ValidationIssueType {
    MISSING_REQUIRED_FIELD,
    INVALID_STATE_ID,
    INVALID_EVENT_HISTORY,
    INCONSISTENT_METADATA,
    CORRUPTED_DATA,
    MISSING_DEPENDENCY
}

/**
 * 验证严重程度
 */
enum class ValidationSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
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

