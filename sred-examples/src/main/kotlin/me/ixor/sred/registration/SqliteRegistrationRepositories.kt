package me.ixor.sred.registration

import me.ixor.sred.core.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant

/**
 * 基于 SQLite 的 RegistrationUserRepository 实现。
 *
 * 使用独立的 SQLite 文件存储用户数据，连接串通过配置项 registration.db.path 注入，
 * 默认为当前工作目录下的 registration_domain.db。
 */
@Repository
class SqliteRegistrationUserRepository(
    @Value("\${registration.db.path:registration_domain.db}")
    private val dbPath: String
) : RegistrationUserRepository {

    private val log = logger<SqliteRegistrationUserRepository>()

    init {
        initialize()
    }

    override fun findByUsername(username: String): RegistrationUser? {
        return queryOne(
            sql = "SELECT username, email, password, status FROM registration_users WHERE username = ?",
            bind = { it.setString(1, username) },
            mapRow = { rs ->
                RegistrationUser(
                    username = rs.getString("username"),
                    email = rs.getString("email"),
                    password = rs.getString("password"),
                    status = RegistrationStatus.valueOf(rs.getString("status"))
                )
            }
        )
    }

    override fun existsByUsername(username: String): Boolean {
        val count = queryOne(
            sql = "SELECT COUNT(1) AS cnt FROM registration_users WHERE username = ?",
            bind = { it.setString(1, username) },
            mapRow = { rs -> rs.getInt("cnt") }
        ) ?: 0
        return count > 0
    }

    override fun save(user: RegistrationUser) {
        execute(
            """
            INSERT INTO registration_users (username, email, password, status, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(username) DO UPDATE SET
                email = excluded.email,
                password = excluded.password,
                status = excluded.status,
                updated_at = excluded.updated_at
            """.trimIndent()
        ) { stmt ->
            stmt.setString(1, user.username)
            stmt.setString(2, user.email)
            stmt.setString(3, user.password)
            stmt.setString(4, user.status.name)
            stmt.setString(5, Instant.now().toString())
        }
    }

    private fun initialize() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS registration_users (
                        username TEXT PRIMARY KEY,
                        email TEXT NOT NULL,
                        password TEXT NOT NULL,
                        status TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        log.info { "SQLite registration_users table initialized at ${File(dbPath).absolutePath}" }
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

    private fun execute(
        sql: String,
        bind: (java.sql.PreparedStatement) -> Unit
    ) {
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                bind(stmt)
                stmt.executeUpdate()
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
 * 基于 SQLite 的验证码仓储实现。
 */
@Repository
class SqliteVerificationCodeRepository(
    @Value("\${registration.db.path:registration_domain.db}")
    private val dbPath: String
) : VerificationCodeRepository {

    private val log = logger<SqliteVerificationCodeRepository>()

    init {
        initialize()
    }

    override fun saveCode(email: String, code: String) {
        execute(
            """
            INSERT INTO verification_codes (email, code, created_at)
            VALUES (?, ?, ?)
            ON CONFLICT(email) DO UPDATE SET
                code = excluded.code,
                created_at = excluded.created_at
            """.trimIndent()
        ) { stmt ->
            stmt.setString(1, email)
            stmt.setString(2, code)
            stmt.setString(3, Instant.now().toString())
        }
    }

    override fun findCode(email: String): String? {
        return queryOne(
            sql = "SELECT code FROM verification_codes WHERE email = ?",
            bind = { it.setString(1, email) },
            mapRow = { rs -> rs.getString("code") }
        )
    }

    private fun initialize() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS verification_codes (
                        email TEXT PRIMARY KEY,
                        code TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        log.info { "SQLite verification_codes table initialized at ${File(dbPath).absolutePath}" }
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

    private fun execute(
        sql: String,
        bind: (java.sql.PreparedStatement) -> Unit
    ) {
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                bind(stmt)
                stmt.executeUpdate()
            }
        }
    }

    private fun getConnection(): Connection {
        val file = File(dbPath)
        val url = "jdbc:sqlite:${file.absolutePath}"
        return DriverManager.getConnection(url)
    }
}

