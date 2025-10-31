package me.ixor.sred.core

import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * 事件的时间语义类型
 * 根据论文3.4节：事件的时间维度
 */
enum class EventTemporalType {
    /**
     * 同步事件：在同一逻辑时刻触发并立即响应
     */
    SYNCHRONOUS,
    
    /**
     * 异步事件：在不同时间或线程中触发
     */
    ASYNCHRONOUS,
    
    /**
     * 延迟事件：事件触发后等待特定条件再生效
     */
    DEFERRED,
    
    /**
     * 周期事件：以时间为因，重复触发状态轮转
     */
    PERIODIC
}

/**
 * 时间事件接口 - 扩展事件的时间语义
 */
interface TemporalEvent : Event {
    /**
     * 事件的时间语义类型
     */
    val temporalType: EventTemporalType
    
    /**
     * 延迟执行时间（仅对DEFERRED类型有效）
     */
    val scheduledTime: Instant?
    
    /**
     * 周期间隔（仅对PERIODIC类型有效）
     */
    val period: Duration?
    
    /**
     * 是否应该在指定时间执行
     */
    fun shouldExecuteAt(time: Instant): Boolean
    
    /**
     * 获取下次执行时间（仅对PERIODIC类型有效）
     */
    fun getNextExecutionTime(): Instant?
}

/**
 * 延迟事件 - 在指定时间后执行
 */
data class DeferredEvent(
    private val baseEvent: Event,
    override val scheduledTime: Instant,
    override val temporalType: EventTemporalType = EventTemporalType.DEFERRED
) : TemporalEvent, Event by baseEvent {
    
    override val period: Duration? = null
    
    override fun shouldExecuteAt(time: Instant): Boolean {
        return time.isAfter(scheduledTime) || time.equals(scheduledTime)
    }
    
    override fun getNextExecutionTime(): Instant? = scheduledTime
    
    override fun isExpired(currentTime: Instant): Boolean {
        return currentTime.isAfter(scheduledTime.plus(Duration.ofDays(1))) // 延迟事件一天后过期
    }
}

/**
 * 周期事件 - 按固定周期重复执行
 */
data class PeriodicEvent(
    private val baseEvent: Event,
    override val period: Duration,
    val startTime: Instant = Instant.now(),
    val endTime: Instant? = null,
    override val temporalType: EventTemporalType = EventTemporalType.PERIODIC,
    private var lastExecutionTime: Instant? = null
) : TemporalEvent, Event by baseEvent {
    
    override val scheduledTime: Instant? = null
    
    override fun shouldExecuteAt(time: Instant): Boolean {
        if (endTime != null && time.isAfter(endTime)) {
            return false
        }
        
        if (time.isBefore(startTime)) {
            return false
        }
        
        val last = lastExecutionTime ?: startTime
        return time.isAfter(last.plus(period)) || time.equals(last.plus(period))
    }
    
    override fun getNextExecutionTime(): Instant? {
        val last = lastExecutionTime ?: startTime
        val next = last.plus(period)
        
        if (endTime != null && next.isAfter(endTime)) {
            return null
        }
        
        return next
    }
    
    /**
     * 记录执行时间
     */
    fun recordExecution(time: Instant): PeriodicEvent {
        return this.copy(lastExecutionTime = time)
    }
    
    override fun isExpired(currentTime: Instant): Boolean {
        return endTime != null && currentTime.isAfter(endTime)
    }
}

/**
 * 同步事件包装器
 */
data class SynchronousEvent(
    private val baseEvent: Event,
    override val temporalType: EventTemporalType = EventTemporalType.SYNCHRONOUS
) : TemporalEvent, Event by baseEvent {
    
    override val scheduledTime: Instant? = null
    override val period: Duration? = null
    
    override fun shouldExecuteAt(time: Instant): Boolean = true
    
    override fun getNextExecutionTime(): Instant? = null
}

/**
 * 异步事件包装器
 */
data class AsynchronousEvent(
    private val baseEvent: Event,
    override val temporalType: EventTemporalType = EventTemporalType.ASYNCHRONOUS
) : TemporalEvent, Event by baseEvent {
    
    override val scheduledTime: Instant? = null
    override val period: Duration? = null
    
    override fun shouldExecuteAt(time: Instant): Boolean = true
    
    override fun getNextExecutionTime(): Instant? = null
}

/**
 * 时间事件工厂
 */
object TemporalEventFactory {
    /**
     * 创建延迟事件
     */
    fun createDeferred(
        event: Event,
        delay: Duration
    ): DeferredEvent {
        return DeferredEvent(
            baseEvent = event,
            scheduledTime = Instant.now().plus(delay)
        )
    }
    
    /**
     * 创建延迟事件（指定执行时间）
     */
    fun createDeferredAt(
        event: Event,
        scheduledTime: Instant
    ): DeferredEvent {
        return DeferredEvent(
            baseEvent = event,
            scheduledTime = scheduledTime
        )
    }
    
    /**
     * 创建周期事件
     */
    fun createPeriodic(
        event: Event,
        period: Duration,
        startTime: Instant = Instant.now(),
        endTime: Instant? = null
    ): PeriodicEvent {
        return PeriodicEvent(
            baseEvent = event,
            period = period,
            startTime = startTime,
            endTime = endTime
        )
    }
    
    /**
     * 创建同步事件
     */
    fun createSynchronous(event: Event): SynchronousEvent {
        return SynchronousEvent(baseEvent = event)
    }
    
    /**
     * 创建异步事件
     */
    fun createAsynchronous(event: Event): AsynchronousEvent {
        return AsynchronousEvent(baseEvent = event)
    }
}

