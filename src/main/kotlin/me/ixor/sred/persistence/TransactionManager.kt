package me.ixor.sred.persistence

import me.ixor.sred.core.logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * 事务ID类型别名
 */
typealias TransactionId = String

/**
 * 事务管理器接口
 * 管理数据库事务的生命周期
 */
interface TransactionManager {
    /**
     * 开始一个新事务
     * @return 事务ID
     */
    suspend fun beginTransaction(): TransactionId
    
    /**
     * 提交事务
     * @param transactionId 事务ID
     */
    suspend fun commitTransaction(transactionId: TransactionId)
    
    /**
     * 回滚事务
     * @param transactionId 事务ID
     */
    suspend fun rollbackTransaction(transactionId: TransactionId)
    
    /**
     * 获取事务对应的数据库连接
     * @param transactionId 事务ID
     * @return 数据库连接，如果事务不存在则返回null
     */
    suspend fun getConnection(transactionId: TransactionId): Connection?
    
    /**
     * 检查事务是否存在
     * @param transactionId 事务ID
     */
    fun hasTransaction(transactionId: TransactionId): Boolean
    
    /**
     * 清理事务资源（用于异常情况）
     * @param transactionId 事务ID
     */
    suspend fun cleanupTransaction(transactionId: TransactionId)
}

/**
 * SQLite事务管理器实现
 */
class SqliteTransactionManager(
    private val connectionFactory: suspend () -> Connection
) : TransactionManager {
    private val log = logger<SqliteTransactionManager>()
    
    // 事务ID -> 数据库连接的映射
    private val transactions = ConcurrentHashMap<TransactionId, Connection>()
    private val mutex = Mutex()
    
    override suspend fun beginTransaction(): TransactionId {
        mutex.withLock {
            val transactionId = generateTransactionId()
            val connection = connectionFactory()
            
            // 设置非自动提交模式
            connection.autoCommit = false
            
            transactions[transactionId] = connection
            log.debug { "开始事务: $transactionId" }
            
            return transactionId
        }
    }
    
    override suspend fun commitTransaction(transactionId: TransactionId) {
        mutex.withLock {
            val connection = transactions.remove(transactionId)
                ?: throw IllegalStateException("Transaction not found: $transactionId")
            
            try {
                connection.commit()
                log.debug { "提交事务: $transactionId" }
            } catch (e: Exception) {
                log.error(e) { "提交事务失败: $transactionId" }
                throw e
            } finally {
                // 恢复自动提交模式并关闭连接
                try {
                    connection.autoCommit = true
                    connection.close()
                } catch (e: Exception) {
                    log.warn(e) { "关闭事务连接失败: $transactionId" }
                }
            }
        }
    }
    
    override suspend fun rollbackTransaction(transactionId: TransactionId) {
        mutex.withLock {
            val connection = transactions.remove(transactionId)
                ?: throw IllegalStateException("Transaction not found: $transactionId")
            
            try {
                connection.rollback()
                log.debug { "回滚事务: $transactionId" }
            } catch (e: Exception) {
                log.error(e) { "回滚事务失败: $transactionId" }
                throw e
            } finally {
                // 恢复自动提交模式并关闭连接
                try {
                    connection.autoCommit = true
                    connection.close()
                } catch (e: Exception) {
                    log.warn(e) { "关闭事务连接失败: $transactionId" }
                }
            }
        }
    }
    
    override suspend fun getConnection(transactionId: TransactionId): Connection? {
        return mutex.withLock {
            transactions[transactionId]
        }
    }
    
    override fun hasTransaction(transactionId: TransactionId): Boolean {
        return transactions.containsKey(transactionId)
    }
    
    override suspend fun cleanupTransaction(transactionId: TransactionId) {
        mutex.withLock {
            val connection = transactions.remove(transactionId) ?: return@withLock
            
            try {
                connection.rollback()
            } catch (e: Exception) {
                log.warn(e) { "清理事务时回滚失败: $transactionId" }
            }
            
            try {
                connection.autoCommit = true
                connection.close()
            } catch (e: Exception) {
                log.warn(e) { "清理事务连接失败: $transactionId" }
            }
            
            log.debug { "清理事务: $transactionId" }
        }
    }
    
    /**
     * 生成唯一的事务ID
     */
    private fun generateTransactionId(): TransactionId {
        return "tx_${System.currentTimeMillis()}_${Thread.currentThread().id}_${transactions.size}"
    }
    
    /**
     * 关闭所有活跃的事务（用于清理资源）
     */
    suspend fun closeAll() {
        mutex.withLock {
            transactions.keys.forEach { transactionId ->
                try {
                    cleanupTransaction(transactionId)
                } catch (e: Exception) {
                    log.error(e) { "关闭事务失败: $transactionId" }
                }
            }
            transactions.clear()
        }
    }
}

/**
 * Coroutine上下文中的事务ID键
 */
object TransactionContextKey : kotlin.coroutines.CoroutineContext.Key<TransactionContextElement>

/**
 * Coroutine上下文中的事务ID元素
 */
data class TransactionContextElement(val transactionId: TransactionId?) : kotlin.coroutines.CoroutineContext.Element {
    override val key: kotlin.coroutines.CoroutineContext.Key<*> = TransactionContextKey
}

/**
 * 从当前协程上下文中获取事务ID
 */
suspend fun getTransactionId(): TransactionId? {
    return coroutineContext.get(TransactionContextKey)?.transactionId
}

/**
 * 在事务上下文中执行代码块
 */
suspend fun <T> withTransaction(
    transactionManager: TransactionManager,
    block: suspend (TransactionId) -> T
): T {
    val transactionId = transactionManager.beginTransaction()
    try {
        val contextWithTx = kotlin.coroutines.coroutineContext + TransactionContextElement(transactionId)
        val result = kotlinx.coroutines.withContext(contextWithTx) {
            block(transactionId)
        }
        transactionManager.commitTransaction(transactionId)
        return result
    } catch (e: Exception) {
        try {
            transactionManager.rollbackTransaction(transactionId)
        } catch (rollbackError: Exception) {
            // 记录回滚错误，但不掩盖原始异常
            val log = logger<TransactionManager>()
            log.error(rollbackError) { "回滚事务失败: $transactionId" }
        }
        throw e
    }
}

