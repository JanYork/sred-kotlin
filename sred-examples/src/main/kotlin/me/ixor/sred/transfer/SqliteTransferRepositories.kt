package me.ixor.sred.transfer

import me.ixor.sred.core.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * 基于 SQLite 的转账用户仓储实现。
 */
@Repository
class SqliteTransferUserRepository(
    @Value("\${transfer.db.path:transfer_domain.db}")
    private val dbPath: String
) : TransferUserRepository {

    private val log = logger<SqliteTransferUserRepository>()

    init {
        initialize()
    }

    override fun findById(userId: String): User? {
        return queryOne(
            sql = "SELECT user_id, name, account_id FROM transfer_users WHERE user_id = ?",
            bind = { it.setString(1, userId) },
            mapRow = { rs ->
                User(
                    userId = rs.getString("user_id"),
                    name = rs.getString("name"),
                    accountId = rs.getString("account_id")
                )
            }
        )
    }

    private fun initialize() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS transfer_users (
                        user_id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        account_id TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        log.info { "SQLite transfer_users table initialized at ${File(dbPath).absolutePath}" }
    }

    private fun <T> queryOne(
        sql: String,
        bind: (java.sql.PreparedStatement) -> Unit,
        mapRow: (ResultSet) -> T
    ): T? {
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                bind(stmt)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) mapRow(rs) else null
                }
            }
        }
    }

    private fun getConnection(): Connection {
        val file = File(dbPath)
        val url = "jdbc:sqlite:${file.absolutePath}"
        return DriverManager.getConnection(url)
    }
}

/**
 * 基于 SQLite 的账户仓储实现。
 */
@Repository
class SqliteAccountRepository(
    @Value("\${transfer.db.path:transfer_domain.db}")
    private val dbPath: String
) : AccountRepository {

    private val log = logger<SqliteAccountRepository>()

    init {
        initialize()
    }

    override fun findById(accountId: String): Account? {
        return queryOne(
            sql = "SELECT account_id, user_id, balance FROM accounts WHERE account_id = ?",
            bind = { it.setString(1, accountId) },
            mapRow = { rs ->
                Account(
                    accountId = rs.getString("account_id"),
                    userId = rs.getString("user_id"),
                    balance = rs.getDouble("balance")
                )
            }
        )
    }

    private fun initialize() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS accounts (
                        account_id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        balance REAL NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        log.info { "SQLite accounts table initialized at ${File(dbPath).absolutePath}" }
    }

    private fun <T> queryOne(
        sql: String,
        bind: (java.sql.PreparedStatement) -> Unit,
        mapRow: (ResultSet) -> T
    ): T? {
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                bind(stmt)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) mapRow(rs) else null
                }
            }
        }
    }

    private fun getConnection(): Connection {
        val file = File(dbPath)
        val url = "jdbc:sqlite:${file.absolutePath}"
        return DriverManager.getConnection(url)
    }
}

