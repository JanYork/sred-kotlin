package me.ixor.sred.persistence

import me.ixor.sred.core.*
import me.ixor.sred.state.StatePersistence
import me.ixor.sred.state.StateHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.*
import java.time.Instant
import java.util.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * SQLite状态持久化实现
 * 
 * 根据论文4.4节：状态上下文的持久化与恢复机制，
 * 实现过程级恢复和语义级自愈能力。
 */
class SqliteStatePersistence(
    private val dbPath: String = "sred_state.db"
) : StatePersistence {
    
    private val objectMapper = jacksonObjectMapper()
    private var connection: Connection? = null
    
    init {
        initializeDatabase()
    }
    
    /**
     * 初始化数据库
     */
    private fun initializeDatabase() {
        val dbFile = File(dbPath)
        val url = "jdbc:sqlite:${dbFile.absolutePath}"
        
        connection = DriverManager.getConnection(url).apply {
            createTables()
        }
    }
    
    /**
     * 创建数据表
     */
    private fun Connection.createTables() {
        createStatement().use { stmt ->
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
    
    override suspend fun saveContext(context: StateContext) {
        withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database connection not initialized")
            
            conn.prepareStatement("""
                INSERT OR REPLACE INTO state_contexts 
                (id, current_state_id, created_at, last_updated_at, local_state, global_state, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                stmt.setString(1, context.id)
                stmt.setString(2, context.currentStateId)
                stmt.setString(3, context.createdAt.toString())
                stmt.setString(4, context.lastUpdatedAt.toString())
                stmt.setString(5, objectMapper.writeValueAsString(context.localState))
                stmt.setString(6, objectMapper.writeValueAsString(context.globalState))
                stmt.setString(7, objectMapper.writeValueAsString(context.metadata))
                stmt.executeUpdate()
            }
        }
    }
    
    override suspend fun loadContext(contextId: ContextId): StateContext? = withContext(Dispatchers.IO) {
        val conn = connection ?: throw IllegalStateException("Database connection not initialized")
        
        conn.prepareStatement("""
            SELECT id, current_state_id, created_at, last_updated_at, local_state, global_state, metadata
            FROM state_contexts WHERE id = ?
        """.trimIndent()).use { stmt ->
            stmt.setString(1, contextId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val recentEvents = loadRecentEvents(contextId)
                    
                    @Suppress("UNCHECKED_CAST")
                    val localStateMap = objectMapper.readValue<Map<String, Any>>(rs.getString("local_state"))
                    @Suppress("UNCHECKED_CAST")
                    val globalStateMap = objectMapper.readValue<Map<String, Any>>(rs.getString("global_state"))
                    @Suppress("UNCHECKED_CAST")
                    val metadataMap = objectMapper.readValue<Map<String, Any>>(rs.getString("metadata"))
                    
                    return@withContext StateContextImpl(
                        id = rs.getString("id"),
                        currentStateId = rs.getString("current_state_id"),
                        createdAt = Instant.parse(rs.getString("created_at")),
                        lastUpdatedAt = Instant.parse(rs.getString("last_updated_at")),
                        localState = localStateMap,
                        globalState = globalStateMap,
                        recentEvents = recentEvents,
                        metadata = metadataMap
                    )
                } else {
                    null
                }
            }
        }
    }
    
    /**
     * 加载最近的事件历史
     */
    private suspend fun loadRecentEvents(contextId: ContextId, limit: Int = 100): List<Event> {
        val conn = connection ?: return emptyList()
        
        return withContext(Dispatchers.IO) {
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
                            val eventData = objectMapper.readValue<Map<String, Any>>(rs.getString("event_data"))
                            val eventTypeStr = rs.getString("event_type")
                            val parts = eventTypeStr.split(":")
                            
                            val event = SimpleEvent(
                                id = rs.getString("event_id"),
                                type = EventType(
                                    name = parts.getOrNull(1) ?: "",
                                    version = parts.getOrNull(2) ?: "1.0",
                                    namespace = parts.getOrNull(0) ?: "default"
                                ),
                                name = rs.getString("event_name"),
                                description = eventData["description"] as? String ?: "",
                                timestamp = Instant.parse(rs.getString("timestamp")),
                                source = eventData["source"] as? String ?: "unknown",
                                priority = EventPriority.values().find { 
                                    it.name == (eventData["priority"] as? String) 
                                } ?: EventPriority.NORMAL,
                                payload = ((eventData["payload"] as? Map<*, *>)?.mapValues { it.value as Any } ?: emptyMap()) as Map<String, Any>,
                                metadata = ((eventData["metadata"] as? Map<*, *>)?.mapValues { it.value as Any } ?: emptyMap()) as Map<String, Any>
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
    
    override suspend fun deleteContext(contextId: ContextId) = withContext(Dispatchers.IO) {
        val conn = connection ?: throw IllegalStateException("Database connection not initialized")
        
        conn.autoCommit = false
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
            
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }
    
    override suspend fun listContextIds(): List<ContextId> = withContext(Dispatchers.IO) {
        val conn = connection ?: throw IllegalStateException("Database connection not initialized")
        
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
    
    /**
     * 保存事件到历史
     */
    suspend fun saveEvent(contextId: ContextId, event: Event) = withContext(Dispatchers.IO) {
        val conn = connection ?: throw IllegalStateException("Database connection not initialized")
        
        val eventData = mapOf(
            "description" to event.description,
            "source" to event.source,
            "priority" to event.priority.name,
            "payload" to event.payload,
            "metadata" to event.metadata
        )
        
        conn.prepareStatement("""
            INSERT INTO event_history 
            (context_id, event_id, event_type, event_name, event_data, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()).use { stmt ->
            stmt.setString(1, contextId)
            stmt.setString(2, event.id)
            stmt.setString(3, event.type.toString())
            stmt.setString(4, event.name)
            stmt.setString(5, objectMapper.writeValueAsString(eventData))
            stmt.setString(6, event.timestamp.toString())
            stmt.executeUpdate()
        }
    }
    
    /**
     * 保存状态历史
     */
    suspend fun saveStateHistory(
        contextId: ContextId,
        fromStateId: StateId?,
        toStateId: StateId,
        eventId: EventId?,
        timestamp: Instant = Instant.now()
    ) = withContext(Dispatchers.IO) {
        val conn = connection ?: throw IllegalStateException("Database connection not initialized")
        
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
    
    /**
     * 获取状态历史
     */
    suspend fun getStateHistory(contextId: ContextId): List<StateHistoryEntry> = withContext(Dispatchers.IO) {
        val conn = connection ?: throw IllegalStateException("Database connection not initialized")
        
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
     * 关闭数据库连接
     */
    fun close() {
        connection?.close()
        connection = null
    }
}

