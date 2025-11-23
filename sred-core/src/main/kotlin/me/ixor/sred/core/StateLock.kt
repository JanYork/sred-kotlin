package me.ixor.sred.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 状态锁接口 - SRED架构的一致性管理
 * 
 * 根据论文4.5节：状态锁（State Lock）用于防止同一实体
 * 在同一时刻进入冲突状态。
 */
interface StateLock {
    /**
     * 尝试获取状态锁
     * @param entityId 实体ID
     * @param stateId 状态ID
     * @param timeout 超时时间
     * @return 是否成功获取锁
     */
    suspend fun tryLock(
        entityId: String,
        stateId: StateId,
        timeout: Duration = Duration.ofSeconds(30)
    ): Boolean
    
    /**
     * 释放状态锁
     * @param entityId 实体ID
     * @param stateId 状态ID
     */
    suspend fun unlock(entityId: String, stateId: StateId)
    
    /**
     * 检查是否已锁定
     * @param entityId 实体ID
     * @param stateId 状态ID
     */
    fun isLocked(entityId: String, stateId: StateId): Boolean
    
    /**
     * 释放实体的所有锁
     * @param entityId 实体ID
     */
    suspend fun releaseAll(entityId: String)
    
    /**
     * 获取锁统计信息
     */
    fun getStatistics(): LockStatistics
}

/**
 * 锁统计信息
 */
data class LockStatistics(
    val totalLocks: Int,
    val lockedEntities: Set<String>,
    val lockedStates: Set<StateId>
)

/**
 * 状态锁实现
 */
class StateLockImpl : StateLock {
    // entityId -> stateId -> Mutex
    private val locks = ConcurrentHashMap<String, ConcurrentHashMap<StateId, Mutex>>()
    private val mutex = Mutex()
    
    override suspend fun tryLock(
        entityId: String,
        stateId: StateId,
        timeout: Duration
    ): Boolean {
        val entityLocks = locks.getOrPut(entityId) { ConcurrentHashMap() }
        val lock = entityLocks.getOrPut(stateId) { Mutex() }
        
        // 使用 withTimeoutOrNull 实现超时机制
        // Mutex.withLock 会等待直到获取锁或协程被取消
        // withTimeoutOrNull 确保在超时时间内无法获取锁时返回 null
        return try {
            withTimeoutOrNull(timeout.toMillis()) {
                lock.withLock {
                    true  // 成功获取锁
                }
            } ?: false  // 超时返回 false
        } catch (e: CancellationException) {
            // 如果协程被取消，返回 false
            false
        }
    }
    
    override suspend fun unlock(entityId: String, stateId: StateId) {
        val entityLocks = locks[entityId] ?: return
        // Mutex不需要显式释放，这里只做清理
        entityLocks.remove(stateId)
        if (entityLocks.isEmpty()) {
            locks.remove(entityId)
        }
    }
    
    override fun isLocked(entityId: String, stateId: StateId): Boolean {
        val entityLocks = locks[entityId] ?: return false
        return entityLocks.containsKey(stateId)
    }
    
    override suspend fun releaseAll(entityId: String) {
        locks.remove(entityId)
    }
    
    override fun getStatistics(): LockStatistics {
        val lockedEntities = locks.keys.toSet()
        val lockedStates = locks.values.flatMap { it.keys }.toSet()
        
        return LockStatistics(
            totalLocks = locks.values.sumOf { it.size },
            lockedEntities = lockedEntities,
            lockedStates = lockedStates
        )
    }
}

/**
 * 改进的状态锁实现（使用超时机制）
 */
class StateLockWithTimeout : StateLock {
    private val locks = ConcurrentHashMap<String, ConcurrentHashMap<StateId, Mutex>>()
    private val lockTimestamps = ConcurrentHashMap<String, ConcurrentHashMap<StateId, Instant>>()
    private val defaultTimeout = Duration.ofMinutes(5)
    
    private fun getOrCreateLock(entityId: String, stateId: StateId): Mutex {
        val entityLocks = locks.getOrPut(entityId) { ConcurrentHashMap() }
        return entityLocks.getOrPut(stateId) { Mutex() }
    }
    
    override suspend fun tryLock(
        entityId: String,
        stateId: StateId,
        timeout: Duration
    ): Boolean {
        val lock = getOrCreateLock(entityId, stateId)
        
        // 使用 withTimeoutOrNull 实现超时机制
        // Mutex.withLock 会等待直到获取锁或协程被取消
        // withTimeoutOrNull 确保在超时时间内无法获取锁时返回 null
        return try {
            withTimeoutOrNull(timeout.toMillis()) {
                lock.withLock {
                    // 记录锁定时间
                    lockTimestamps.getOrPut(entityId) { ConcurrentHashMap() }[stateId] = Instant.now()
                    true  // 成功获取锁
                }
            } ?: false  // 超时返回 false
        } catch (e: CancellationException) {
            // 如果协程被取消，返回 false
            false
        }
    }
    
    override suspend fun unlock(entityId: String, stateId: StateId) {
        val entityLocks = locks[entityId] ?: return
        entityLocks.remove(stateId)
        lockTimestamps[entityId]?.remove(stateId)
        
        if (entityLocks.isEmpty()) {
            locks.remove(entityId)
            lockTimestamps.remove(entityId)
        }
    }
    
    override fun isLocked(entityId: String, stateId: StateId): Boolean {
        val entityLocks = locks[entityId] ?: return false
        if (!entityLocks.containsKey(stateId)) return false
        
        // 检查是否超时
        val timestamp = lockTimestamps[entityId]?.get(stateId)
        if (timestamp != null && Instant.now().isAfter(timestamp.plus(defaultTimeout))) {
            // 锁已超时，自动释放
            entityLocks.remove(stateId)
            lockTimestamps[entityId]?.remove(stateId)
            return false
        }
        
        return true
    }
    
    override suspend fun releaseAll(entityId: String) {
        locks.remove(entityId)
        lockTimestamps.remove(entityId)
    }
    
    override fun getStatistics(): LockStatistics {
        val lockedEntities = locks.keys.toSet()
        val lockedStates = locks.values.flatMap { it.keys }.toSet()
        
        return LockStatistics(
            totalLocks = locks.values.sumOf { it.size },
            lockedEntities = lockedEntities,
            lockedStates = lockedStates
        )
    }
}

/**
 * 状态锁工厂
 */
object StateLockFactory {
    fun create(withTimeout: Boolean = true): StateLock {
        return if (withTimeout) {
            StateLockWithTimeout()
        } else {
            StateLockImpl()
        }
    }
}

