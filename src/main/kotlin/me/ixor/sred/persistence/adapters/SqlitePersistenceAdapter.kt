package me.ixor.sred.persistence.adapters

import me.ixor.sred.core.*
import me.ixor.sred.persistence.AbstractPersistenceAdapter
import me.ixor.sred.persistence.ExtendedStatePersistence
import me.ixor.sred.persistence.SqlitePersistenceConfig
import me.ixor.sred.persistence.TransactionManager
import me.ixor.sred.persistence.SqliteTransactionManager
import me.ixor.sred.persistence.TransactionId
import me.ixor.sred.persistence.TransactionContextKey
import me.ixor.sred.state.StateHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.Closeable
import java.io.File
import java.sql.*
import java.time.Instant

/**
 * SQLite持久化适配器
 * 
 * 基于JDBC的SQLite实现，提供状态上下文的持久化与恢复功能
 * 实现 Closeable 接口，支持资源自动管理
 */
class SqlitePersistenceAdapter(
    config: SqlitePersistenceConfig = SqlitePersistenceConfig()
) : AbstractPersistenceAdapter(config), ExtendedStatePersistence, Closeable {
    
    private val log = logger<SqlitePersistenceAdapter>()
    private var connection: Connection? = null
    @Volatile
    private var closed = false
    
    // 事务管理器
    private val transactionManager: TransactionManager by lazy {
        SqliteTransactionManager {
            // 创建新连接用于事务
            val dbFile = File(config.connectionString)
            val url = "jdbc:sqlite:${dbFile.absolutePath}"
            val conn = DriverManager.getConnection(url)
            // 确保表已创建
            createTables(conn)
            conn
        }
    }
    
    init {
        // 初始化在initialize方法中进行，支持异步初始化
    }
    
    /**
     * 获取数据库连接（支持事务上下文）
     * @param transactionId 可选的事务ID
     * @return 数据库连接
     */
    private suspend fun getConnection(transactionId: TransactionId? = null): Connection {
        // 如果提供了事务ID，使用事务连接
        transactionId?.let { txId ->
            transactionManager.getConnection(txId)?.let { return it }
            throw PersistenceException("Transaction not found: $txId")
        }
        
        // 尝试从协程上下文获取事务ID
        coroutineContext.get(TransactionContextKey)?.transactionId?.let { txId ->
            transactionManager.getConnection(txId)?.let { return it }
        }
        
        // 否则使用默认连接（自动提交）
        initialize() // 确保已初始化
        ensureOpen()
        return connection ?: throw PersistenceException("Connection is null after ensureOpen()")
    }
    
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        if (closed) {
            throw PersistenceException("Adapter has been closed")
        }
        if (connection != null) return@withContext
        
        try {
            val dbFile = File(config.connectionString)
            val url = "jdbc:sqlite:${dbFile.absolutePath}"
            
            log.debug { "Initializing SQLite connection to: ${dbFile.absolutePath}" }
            
            connection = DriverManager.getConnection(url).also {
                createTables(it)
            }
            
            log.info { "SQLite connection initialized successfully" }
        } catch (e: Exception) {
            log.error(e) { "Failed to initialize SQLite connection" }
            throw PersistenceException("Failed to initialize SQLite connection: ${e.message}", e)
        }
    }
    
    /**
     * 创建数据表
     */
    private fun createTables(conn: Connection) {
        conn.createStatement().use { stmt ->
            // 状态上下文表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS state_contexts (
                    id TEXT PRIMARY KEY,
                    current_state_id TEXT,
                    created_at TEXT NOT NULL,
                    last_updated_at TEXT NOT NULL,
                    local_state TEXT,
                    global_state TEXT,
                    metadata TEXT
                )
            """.trimIndent())
            
            // 事件历史表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS event_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    context_id TEXT NOT NULL,
                    event_id TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    event_name TEXT NOT NULL,
                    event_data TEXT,
                    timestamp TEXT NOT NULL,
                    FOREIGN KEY (context_id) REFERENCES state_contexts(id)
                )
            """.trimIndent())
            
            // 状态历史表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS state_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    context_id TEXT NOT NULL,
                    from_state_id TEXT,
                    to_state_id TEXT NOT NULL,
                    event_id TEXT,
                    timestamp TEXT NOT NULL,
                    FOREIGN KEY (context_id) REFERENCES state_contexts(id)
                )
            """.trimIndent())
            
            // 状态转移表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS state_transitions (
                    id TEXT PRIMARY KEY,
                    from_state_id TEXT NOT NULL,
                    to_state_id TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    condition_data TEXT,
                    action_data TEXT,
                    priority INTEGER DEFAULT 0,
                    enabled INTEGER DEFAULT 1,
                    created_at TEXT NOT NULL
                )
            """.trimIndent())
            
            // 创建索引
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_context_updated ON state_contexts(last_updated_at)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_event_context ON event_history(context_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_history_context ON state_history(context_id)")
        }
    }
    
    override suspend fun saveContext(context: StateContext, transactionId: TransactionId?) {
        withContext(Dispatchers.IO) {
            val conn = getConnection(transactionId)
            
            conn.prepareStatement("""
                INSERT OR REPLACE INTO state_contexts 
                (id, current_state_id, created_at, last_updated_at, local_state, global_state, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, context.id)
                stmt.setString(2, context.currentStateId)
                stmt.setString(3, context.createdAt.toString())
                stmt.setString(4, context.lastUpdatedAt.toString())
                stmt.setString(5, serialize(context.localState))
                stmt.setString(6, serialize(context.globalState))
                stmt.setString(7, serialize(context.metadata))
                stmt.executeUpdate()
            }
        }
    }
    
    override suspend fun loadContext(contextId: ContextId, transactionId: TransactionId?): StateContext? = withContext(Dispatchers.IO) {
        val conn = getConnection(transactionId)
        
        conn.prepareStatement("""
            SELECT id, current_state_id, created_at, last_updated_at, local_state, global_state, metadata
            FROM state_contexts WHERE id = ?
        """.trimIndent()).use { stmt ->
            stmt.setString(1, contextId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return@withContext buildStateContext(
                        id = rs.getString("id"),
                        currentStateId = rs.getString("current_state_id"),
                        createdAt = Instant.parse(rs.getString("created_at")),
                        lastUpdatedAt = Instant.parse(rs.getString("last_updated_at")),
                        localStateJson = rs.getString("local_state"),
                        globalStateJson = rs.getString("global_state"),
                        metadataJson = rs.getString("metadata"),
                        contextId = contextId
                    )
                } else {
                    null
                }
            }
        }
    }
    
    override suspend fun deleteContext(contextId: ContextId, transactionId: TransactionId?) = withContext(Dispatchers.IO) {
        val conn = getConnection(transactionId)
        
        // 如果没有事务，需要手动管理事务
        val isInTransaction = transactionId != null || coroutineContext.get(TransactionContextKey) != null
        val originalAutoCommit = conn.autoCommit
        
        if (!isInTransaction) {
            conn.autoCommit = false
        }
        
        try {
            // 删除关联的事件历史
            conn.prepareStatement("DELETE FROM event_history WHERE context_id = ?").use { stmt ->
                stmt.setString(1, contextId)
                stmt.executeUpdate()
            }
            
            // 删除关联的状态历史
            conn.prepareStatement("DELETE FROM state_history WHERE context_id = ?").use { stmt ->
                stmt.setString(1, contextId)
                stmt.executeUpdate()
            }
            
            // 删除上下文
            conn.prepareStatement("DELETE FROM state_contexts WHERE id = ?").use { stmt ->
                stmt.setString(1, contextId)
                stmt.executeUpdate()
            }
            
            if (!isInTransaction) {
                conn.commit()
            }
        } catch (e: Exception) {
            if (!isInTransaction) {
                conn.rollback()
            }
            throw e
        } finally {
            if (!isInTransaction) {
                conn.autoCommit = originalAutoCommit
            }
        }
    }
    
    override suspend fun listContextIds(transactionId: TransactionId?): List<ContextId> = withContext(Dispatchers.IO) {
        val conn = getConnection(transactionId)
        
        conn.prepareStatement("SELECT id FROM state_contexts ORDER BY last_updated_at DESC").use { stmt ->
            stmt.executeQuery().use { rs ->
                val ids = mutableListOf<ContextId>()
                while (rs.next()) {
                    ids.add(rs.getString("id"))
                }
                ids
            }
        }
    }
    
    override suspend fun loadRecentEvents(contextId: ContextId, limit: Int): List<Event> {
        return withContext(Dispatchers.IO) {
            // loadRecentEvents 通常是从已加载的上下文中调用，使用默认连接
            // 如果需要事务支持，可以扩展接口添加 transactionId 参数
            val conn = connection ?: run {
                initialize()
                connection ?: return@withContext emptyList()
            }
            conn.prepareStatement("""
                SELECT event_id, event_type, event_name, event_data, timestamp
                FROM event_history
                WHERE context_id = ?
                ORDER BY timestamp DESC
                LIMIT ?
            """.trimIndent()).use { stmt ->
                stmt.setString(1, contextId)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    val events = mutableListOf<Event>()
                    while (rs.next()) {
                        try {
                            val eventData = deserialize<Map<String, Any>>(rs.getString("event_data"))
                            val timestamp = Instant.parse(rs.getString("timestamp"))
                            
                            val event = buildEventFromData(
                                eventId = rs.getString("event_id"),
                                eventTypeStr = rs.getString("event_type"),
                                eventName = rs.getString("event_name"),
                                eventData = eventData,
                                timestamp = timestamp
                            )
                            events.add(event)
                        } catch (e: Exception) {
                            // 跳过无法解析的事件
                        }
                    }
                    events.reversed() // 恢复时间顺序
                }
            }
        }
    }
    
    override suspend fun saveEvent(contextId: ContextId, event: Event, transactionId: TransactionId?) {
        withContext(Dispatchers.IO) {
            val conn = getConnection(transactionId)
            
            val eventData = buildEventData(event)
            
            conn.prepareStatement("""
                INSERT INTO event_history 
                (context_id, event_id, event_type, event_name, event_data, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, contextId)
                stmt.setString(2, event.id)
                stmt.setString(3, event.type.toString())
                stmt.setString(4, event.name)
                stmt.setString(5, serialize(eventData))
                stmt.setString(6, event.timestamp.toString())
                stmt.executeUpdate()
            }
        }
    }
    
    override suspend fun saveStateHistory(
        contextId: ContextId,
        fromStateId: StateId?,
        toStateId: StateId,
        eventId: EventId?,
        timestamp: Instant,
        transactionId: TransactionId?
    ) {
        withContext(Dispatchers.IO) {
            val conn = getConnection(transactionId)
            
            conn.prepareStatement("""
                INSERT INTO state_history 
                (context_id, from_state_id, to_state_id, event_id, timestamp)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, contextId)
                stmt.setString(2, fromStateId)
                stmt.setString(3, toStateId)
                stmt.setString(4, eventId)
                stmt.setString(5, timestamp.toString())
                stmt.executeUpdate()
            }
        }
    }
    
    override suspend fun getStateHistory(contextId: ContextId, transactionId: TransactionId?): List<StateHistoryEntry> = withContext(Dispatchers.IO) {
        val conn = getConnection(transactionId)
        
        conn.prepareStatement("""
            SELECT from_state_id, to_state_id, event_id, timestamp
            FROM state_history
            WHERE context_id = ?
            ORDER BY timestamp ASC
        """.trimIndent()).use { stmt ->
            stmt.setString(1, contextId)
            stmt.executeQuery().use { rs ->
                val history = mutableListOf<StateHistoryEntry>()
                val events = loadRecentEvents(contextId, Int.MAX_VALUE).associateBy { it.id }
                
                while (rs.next()) {
                    val eventId = rs.getString("event_id")
                    history.add(
                        StateHistoryEntry(
                            timestamp = Instant.parse(rs.getString("timestamp")),
                            fromStateId = rs.getString("from_state_id"),
                            toStateId = rs.getString("to_state_id"),
                            event = events[eventId],
                            contextId = contextId
                        )
                    )
                }
                history
            }
        }
    }
    
    /**
     * 查询所有暂停的实例ID
     * 通过查询 metadata JSON 中包含 _pausedAt 键的实例
     */
    override suspend fun findPausedInstances(transactionId: TransactionId?): List<ContextId> = withContext(Dispatchers.IO) {
        val conn = getConnection(transactionId)
        
        // SQLite 支持 JSON1 扩展，可以使用 json_extract 函数
        // 如果 metadata 包含 _pausedAt 键，则认为是暂停的实例
        val pausedInstances = mutableListOf<ContextId>()
        
        try {
            conn.prepareStatement("""
                SELECT id 
                FROM state_contexts 
                WHERE metadata IS NOT NULL 
                AND metadata != '{}'
                AND json_extract(metadata, '$._pausedAt') IS NOT NULL
            """.trimIndent()).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        pausedInstances.add(rs.getString("id"))
                    }
                }
            }
        } catch (e: SQLException) {
            // 如果 JSON1 扩展不可用，回退到加载所有上下文并过滤
            log.warn(e) { "JSON1 extension not available, falling back to loading all contexts" }
            
            conn.prepareStatement("SELECT id, metadata FROM state_contexts WHERE metadata IS NOT NULL").use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val id = rs.getString("id")
                        val metadataJson = rs.getString("metadata")
                        if (metadataJson != null && metadataJson.contains("\"_pausedAt\"")) {
                            pausedInstances.add(id)
                        }
                    }
                }
            }
        }
        
        pausedInstances
    }
    
    override fun close() {
        // 使用 @Volatile 的 closed 标志进行双重检查锁定（线程安全）
        if (closed) return
        
        synchronized(this) {
            // 再次检查，防止多线程环境下的重复关闭
            if (closed) return
            closed = true
            
            try {
                // 关闭所有活跃的事务
                kotlinx.coroutines.runBlocking {
                    try {
                        (transactionManager as? SqliteTransactionManager)?.closeAll()
                    } catch (e: Exception) {
                        log.warn(e) { "Error closing transactions" }
                    }
                }
                
                val conn = connection
                if (conn != null && !conn.isClosed) {
                    // 先关闭所有活跃的 Statement
                    try {
                        // SQLite JDBC 驱动会自动关闭关联的 Statement，但显式关闭更安全
                        conn.close()
                        log.debug { "SQLite connection closed" }
                    } catch (e: SQLException) {
                        log.warn(e) { "Error closing SQLite connection (non-critical)" }
                    }
                }
            } catch (e: Exception) {
                log.error(e) { "Error closing SQLite connection" }
            } finally {
                connection = null
            }
        }
    }
    
    /**
     * 检查适配器是否已关闭
     */
    fun isClosed(): Boolean = closed
    
    /**
     * 确保连接可用，如果已关闭则抛出异常
     */
    private fun ensureOpen() {
        if (closed || connection == null) {
            throw PersistenceException("Persistence adapter is closed or not initialized")
        }
    }
}

