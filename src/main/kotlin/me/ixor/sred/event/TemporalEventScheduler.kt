package me.ixor.sred.event

import me.ixor.sred.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/**
 * 时间事件调度器 - 处理延迟和周期事件
 * 
 * 根据论文3.4节：事件的时间维度
 */
interface TemporalEventScheduler {
    /**
     * 事件总线
     */
    val eventBus: EventBus
    
    /**
     * 调度延迟事件
     */
    suspend fun scheduleDeferred(
        event: Event,
        delay: Duration
    )
    
    /**
     * 调度延迟事件（指定执行时间）
     */
    suspend fun scheduleDeferredAt(
        event: Event,
        scheduledTime: Instant
    )
    
    /**
     * 调度周期事件
     */
    suspend fun schedulePeriodic(
        event: Event,
        period: Duration,
        startTime: Instant = Instant.now(),
        endTime: Instant? = null
    ): String // 返回调度ID
    
    /**
     * 取消周期事件调度
     */
    suspend fun cancelPeriodic(scheduleId: String)
    
    /**
     * 启动调度器
     */
    suspend fun start()
    
    /**
     * 停止调度器
     */
    suspend fun stop()
    
    /**
     * 获取调度统计信息
     */
    fun getStatistics(): SchedulerStatistics
}

/**
 * 调度统计信息
 */
data class SchedulerStatistics(
    val totalDeferredEvents: Int,
    val totalPeriodicEvents: Int,
    val activeDeferredEvents: Int,
    val activePeriodicEvents: Int,
    val executedDeferredEvents: Long,
    val executedPeriodicEvents: Long
)

/**
 * 时间事件调度器实现
 */
class TemporalEventSchedulerImpl(
    override val eventBus: EventBus,
    private val checkInterval: Duration = Duration.ofMillis(100)
) : TemporalEventScheduler {
    
    private val deferredEvents = ConcurrentHashMap<String, DeferredEventInfo>()
    private val periodicEvents = ConcurrentHashMap<String, PeriodicEventInfo>()
    private val mutex = Mutex()
    private var isRunning = false
    private var schedulerJob: Job? = null
    
    private var executedDeferredCount = 0L
    private var executedPeriodicCount = 0L
    
    /**
     * 延迟事件信息
     */
    private data class DeferredEventInfo(
        val event: Event,
        val scheduledTime: Instant,
        val scheduleId: String
    )
    
    /**
     * 周期事件信息
     */
    private data class PeriodicEventInfo(
        val event: Event,
        val period: Duration,
        val startTime: Instant,
        val endTime: Instant?,
        var lastExecutionTime: Instant?,
        val scheduleId: String
    )
    
    override suspend fun scheduleDeferred(
        event: Event,
        delay: Duration
    ) {
        val scheduledTime = Instant.now().plus(delay)
        scheduleDeferredAt(event, scheduledTime)
    }
    
    override suspend fun scheduleDeferredAt(
        event: Event,
        scheduledTime: Instant
    ) {
        val scheduleId = "${event.id}_deferred_${System.currentTimeMillis()}"
        val deferred = TemporalEventFactory.createDeferredAt(event, scheduledTime)
        
        mutex.withLock {
            deferredEvents[scheduleId] = DeferredEventInfo(
                event = deferred,
                scheduledTime = scheduledTime,
                scheduleId = scheduleId
            )
        }
    }
    
    override suspend fun schedulePeriodic(
        event: Event,
        period: Duration,
        startTime: Instant,
        endTime: Instant?
    ): String {
        val scheduleId = "${event.id}_periodic_${System.currentTimeMillis()}"
        val periodic = TemporalEventFactory.createPeriodic(event, period, startTime, endTime)
        
        mutex.withLock {
            periodicEvents[scheduleId] = PeriodicEventInfo(
                event = periodic,
                period = period,
                startTime = startTime,
                endTime = endTime,
                lastExecutionTime = null,
                scheduleId = scheduleId
            )
        }
        
        return scheduleId
    }
    
    override suspend fun cancelPeriodic(scheduleId: String) {
        mutex.withLock {
            periodicEvents.remove(scheduleId)
        }
    }
    
    override suspend fun start() {
        mutex.withLock {
            if (isRunning) return
            
            isRunning = true
            schedulerJob = CoroutineScope(Dispatchers.Default).launch {
                schedulerLoop()
            }
        }
    }
    
    override suspend fun stop() {
        mutex.withLock {
            if (!isRunning) return
            
            isRunning = false
            schedulerJob?.cancel()
            schedulerJob?.join()
            
            deferredEvents.clear()
            periodicEvents.clear()
        }
    }
    
    /**
     * 调度器主循环
     */
    private suspend fun schedulerLoop() {
        val scope = CoroutineScope(coroutineContext)
        while (scope.isActive && isRunning) {
            try {
                val now = Instant.now()
                
                // 处理延迟事件
                processDeferredEvents(now)
                
                // 处理周期事件
                processPeriodicEvents(now)
                
                delay(checkInterval.toMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 继续运行，记录错误
            }
        }
    }
    
    /**
     * 处理延迟事件
     */
    private suspend fun processDeferredEvents(now: Instant) {
        val toExecute = mutex.withLock {
            deferredEvents.values.filter { deferred ->
                (deferred.event as? TemporalEvent)?.shouldExecuteAt(now) ?: false
            }.toList()
        }
        
        for (deferred in toExecute) {
            try {
                eventBus.publish(deferred.event)
                mutex.withLock {
                    deferredEvents.remove(deferred.scheduleId)
                    executedDeferredCount++
                }
            } catch (e: Exception) {
                // 发布失败，但不移除，稍后重试
            }
        }
    }
    
    /**
     * 处理周期事件
     */
    private suspend fun processPeriodicEvents(now: Instant) {
        val toExecute = mutex.withLock {
            periodicEvents.values.filter { periodic ->
                (periodic.event as? TemporalEvent)?.shouldExecuteAt(now) ?: false
            }.toList()
        }
        
        for (periodic in toExecute) {
            try {
                eventBus.publish(periodic.event)
                
                mutex.withLock {
                    val updated = periodic.copy(lastExecutionTime = now)
                    periodicEvents[periodic.scheduleId] = updated
                    executedPeriodicCount++
                }
                
                // 检查是否应该结束
                if (periodic.endTime != null && now.isAfter(periodic.endTime)) {
                    mutex.withLock {
                        periodicEvents.remove(periodic.scheduleId)
                    }
                }
            } catch (e: Exception) {
                // 发布失败，继续
            }
        }
    }
    
    override fun getStatistics(): SchedulerStatistics = runBlocking {
        mutex.withLock {
            SchedulerStatistics(
                totalDeferredEvents = deferredEvents.size,
                totalPeriodicEvents = periodicEvents.size,
                activeDeferredEvents = deferredEvents.size,
                activePeriodicEvents = periodicEvents.size,
                executedDeferredEvents = executedDeferredCount,
                executedPeriodicEvents = executedPeriodicCount
            )
        }
    }
}

/**
 * 时间事件调度器工厂
 */
object TemporalEventSchedulerFactory {
    fun create(
        eventBus: EventBus,
        checkInterval: Duration = Duration.ofMillis(100)
    ): TemporalEventScheduler {
        return TemporalEventSchedulerImpl(eventBus, checkInterval)
    }
}

