package me.ixor.sred.persistence

import me.ixor.sred.state.StatePersistence
import me.ixor.sred.persistence.adapters.SqlitePersistenceAdapter

/**
 * 持久化适配器工厂
 * 
 * 用于创建不同类型的持久化适配器实例
 */
object PersistenceAdapterFactory {
    
    /**
     * 根据配置创建持久化适配器
     */
    fun create(config: PersistenceAdapterConfig): StatePersistence {
        return when (config.databaseType) {
            DatabaseType.SQLITE -> {
                val sqliteConfig = config as? SqlitePersistenceConfig
                    ?: SqlitePersistenceConfig(dbPath = config.connectionString, properties = config.properties)
                SqlitePersistenceAdapter(sqliteConfig)
            }
            DatabaseType.IN_MEMORY -> {
                // 使用内存实现
                me.ixor.sred.state.InMemoryStatePersistence()
            }
            DatabaseType.MYSQL -> {
                // TODO: 实现MySQL适配器
                throw UnsupportedOperationException("MySQL adapter not yet implemented")
            }
            DatabaseType.POSTGRESQL -> {
                // TODO: 实现PostgreSQL适配器
                throw UnsupportedOperationException("PostgreSQL adapter not yet implemented")
            }
            DatabaseType.H2 -> {
                // TODO: 实现H2适配器
                throw UnsupportedOperationException("H2 adapter not yet implemented")
            }
            DatabaseType.MONGODB -> {
                // TODO: 实现MongoDB适配器
                throw UnsupportedOperationException("MongoDB adapter not yet implemented")
            }
            DatabaseType.REDIS -> {
                // TODO: 实现Redis适配器
                throw UnsupportedOperationException("Redis adapter not yet implemented")
            }
        }
    }
    
    /**
     * 创建SQLite适配器（便捷方法）
     */
    fun createSqlite(dbPath: String = "sred_state.db"): StatePersistence {
        return create(SqlitePersistenceConfig(dbPath))
    }
    
    /**
     * 创建SQLite适配器（返回具体类型）
     */
    fun createSqliteAdapter(dbPath: String = "sred_state.db"): SqlitePersistenceAdapter {
        val config = SqlitePersistenceConfig(dbPath)
        return SqlitePersistenceAdapter(config)
    }
    
    /**
     * 创建MySQL适配器（便捷方法）
     */
    fun createMysql(
        host: String = "localhost",
        port: Int = 3306,
        database: String,
        username: String? = null,
        password: String? = null,
        properties: Map<String, Any> = emptyMap()
    ): StatePersistence {
        val connectionString = "jdbc:mysql://$host:$port/$database"
        val config = MysqlPersistenceConfig(
            connectionString = connectionString,
            username = username,
            password = password,
            database = database,
            host = host,
            port = port,
            properties = properties
        )
        return create(config)
    }
    
    /**
     * 创建PostgreSQL适配器（便捷方法）
     */
    fun createPostgresql(
        host: String = "localhost",
        port: Int = 5432,
        database: String,
        username: String? = null,
        password: String? = null,
        properties: Map<String, Any> = emptyMap()
    ): StatePersistence {
        val connectionString = "jdbc:postgresql://$host:$port/$database"
        val config = PostgresqlPersistenceConfig(
            connectionString = connectionString,
            username = username,
            password = password,
            database = database,
            host = host,
            port = port,
            properties = properties
        )
        return create(config)
    }
    
    /**
     * 创建内存适配器（便捷方法）
     */
    fun createInMemory(): StatePersistence {
        return me.ixor.sred.state.InMemoryStatePersistence()
    }
}

/**
 * DSL风格的配置构建器
 */
fun persistenceAdapterConfig(block: PersistenceAdapterConfigBuilder.() -> Unit): PersistenceAdapterConfig {
    val builder = PersistenceAdapterConfigBuilder()
    builder.block()
    return builder.build()
}

class PersistenceAdapterConfigBuilder {
    var databaseType: DatabaseType = DatabaseType.SQLITE
    var connectionString: String = "sred_state.db"
    var username: String? = null
    var password: String? = null
    var properties: Map<String, Any> = emptyMap()
    
    fun build(): PersistenceAdapterConfig {
        return when (databaseType) {
            DatabaseType.SQLITE -> SqlitePersistenceConfig(
                dbPath = connectionString,
                properties = properties
            )
            DatabaseType.MYSQL -> {
                val parts = connectionString.replace("jdbc:mysql://", "").split("/")
                val hostPort = parts[0].split(":")
                MysqlPersistenceConfig(
                    connectionString = connectionString,
                    username = username,
                    password = password,
                    database = parts.getOrNull(1) ?: "",
                    host = hostPort[0],
                    port = hostPort.getOrNull(1)?.toIntOrNull() ?: 3306,
                    properties = properties
                )
            }
            DatabaseType.POSTGRESQL -> {
                val parts = connectionString.replace("jdbc:postgresql://", "").split("/")
                val hostPort = parts[0].split(":")
                PostgresqlPersistenceConfig(
                    connectionString = connectionString,
                    username = username,
                    password = password,
                    database = parts.getOrNull(1) ?: "",
                    host = hostPort[0],
                    port = hostPort.getOrNull(1)?.toIntOrNull() ?: 5432,
                    properties = properties
                )
            }
            DatabaseType.IN_MEMORY -> object : PersistenceAdapterConfig {
                override val databaseType: DatabaseType = DatabaseType.IN_MEMORY
                override val connectionString: String = "memory"
                override val username: String? = null
                override val password: String? = null
                override val properties: Map<String, Any> = emptyMap()
            }
            else -> throw IllegalArgumentException("Unsupported database type: $databaseType")
        }
    }
}

