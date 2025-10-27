package me.ixor.sred.event

import me.ixor.sred.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 事件发射器 - 负责产生和发布事件
 * 
 * 事件发射器是事件源（Emitter），产生事件的主体。
 * 它封装了事件的创建和发布逻辑。
 */
interface EventEmitter {
    /**
     * 发射器ID
     */
    val id: String
    
    /**
     * 发射器名称
     */
    val name: String
    
    /**
     * 发射器类型
     */
    val type: EmitterType
    
    /**
     * 是否启用
     */
    val enabled: Boolean
    
    /**
     * 发射事件
     */
    suspend fun emit(event: Event)
    
    /**
     * 发射事件（使用构建器）
     */
    suspend fun emit(
        type: EventType,
        name: String,
        description: String = "",
        priority: EventPriority = EventPriority.NORMAL,
        payload: Map<String, Any> = emptyMap(),
        metadata: Map<String, Any> = emptyMap()
    )
    
    /**
     * 启动发射器
     */
    suspend fun start()
    
    /**
     * 停止发射器
     */
    suspend fun stop()
    
    /**
     * 获取发射统计信息
     */
    fun getStatistics(): EmitterStatistics
}

/**
 * 发射器类型
 */
enum class EmitterType {
    MANUAL,      // 手动发射
    SCHEDULED,   // 定时发射
    REACTIVE,    // 响应式发射
    STREAM       // 流式发射
}

/**
 * 发射器统计信息
 */
data class EmitterStatistics(
    val totalEmitted: Long,
    val successCount: Long,
    val errorCount: Long,
    val lastEmittedAt: Instant?,
    val averageEmitTimeMs: Double
)

/**
 * 抽象事件发射器实现
 */
abstract class AbstractEventEmitter(
    override val id: String,
    override val name: String,
    override val type: EmitterType,
    override val enabled: Boolean = true
) : EventEmitter {
    private val statistics = EmitterStatisticsImpl()
    private var isRunning = false
    
    override suspend fun emit(event: Event) {
        if (!enabled || !isRunning) return
        
        val startTime = System.currentTimeMillis()
        
        try {
            doEmit(event)
            statistics.recordSuccess()
            statistics.updateEmitTime(System.currentTimeMillis() - startTime)
        } catch (e: Exception) {
            statistics.recordError()
            throw e
        }
    }
    
    override suspend fun emit(
        type: EventType,
        name: String,
        description: String,
        priority: EventPriority,
        payload: Map<String, Any>,
        metadata: Map<String, Any>
    ) {
        val event = EventFactory.create(
            type = type,
            name = name,
            description = description,
            source = this.id,
            priority = priority,
            payload = payload
        ).let { baseEvent ->
            // 添加元数据
            if (metadata.isNotEmpty()) {
                EventFactory.builder()
                    .id(baseEvent.id)
                    .type(baseEvent.type)
                    .name(baseEvent.name)
                    .description(baseEvent.description)
                    .timestamp(baseEvent.timestamp)
                    .source(baseEvent.source)
                    .priority(baseEvent.priority)
                    .apply { baseEvent.payload.forEach { (k, v) -> payload(k, v) } }
                    .apply { metadata.forEach { (k, v) -> metadata(k, v) } }
                    .build()
            } else {
                baseEvent
            }
        }
        
        emit(event)
    }
    
    override suspend fun start() {
        isRunning = true
        onStart()
    }
    
    override suspend fun stop() {
        isRunning = false
        onStop()
    }
    
    override fun getStatistics(): EmitterStatistics = runBlocking { statistics.getStatistics() }
    
    /**
     * 实际发射事件的实现
     */
    protected abstract suspend fun doEmit(event: Event)
    
    /**
     * 启动时的钩子
     */
    protected open suspend fun onStart() {}
    
    /**
     * 停止时的钩子
     */
    protected open suspend fun onStop() {}
}

/**
 * 基于事件总线的发射器
 */
class EventBusEmitter(
    id: String,
    name: String,
    type: EmitterType = EmitterType.MANUAL,
    private val eventBus: EventBus,
    enabled: Boolean = true
) : AbstractEventEmitter(id, name, type, enabled) {
    
    override suspend fun doEmit(event: Event) {
        eventBus.publish(event)
    }
}

/**
 * 定时发射器
 */
class ScheduledEmitter(
    id: String,
    name: String,
    private val eventBus: EventBus,
    private val intervalMs: Long,
    private val eventFactory: suspend () -> Event,
    enabled: Boolean = true
) : AbstractEventEmitter(id, name, EmitterType.SCHEDULED, enabled) {
    
    private var scheduledJob: Job? = null
    
    override suspend fun doEmit(event: Event) {
        eventBus.publish(event)
    }
    
    override suspend fun onStart() {
        scheduledJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                try {
                    val event = eventFactory()
                    emit(event)
                } catch (e: Exception) {
                    // Log error but continue
                }
                delay(intervalMs)
            }
        }
    }
    
    override suspend fun onStop() {
        scheduledJob?.cancel()
        scheduledJob?.join()
    }
}

/**
 * 响应式发射器 - 基于外部触发器
 */
class ReactiveEmitter(
    id: String,
    name: String,
    private val eventBus: EventBus,
    enabled: Boolean = true
) : AbstractEventEmitter(id, name, EmitterType.REACTIVE, enabled) {
    
    private val triggers = ConcurrentHashMap<String, suspend () -> Event>()
    
    override suspend fun doEmit(event: Event) {
        eventBus.publish(event)
    }
    
    /**
     * 注册触发器
     */
    fun registerTrigger(triggerId: String, eventFactory: suspend () -> Event) {
        triggers[triggerId] = eventFactory
    }
    
    /**
     * 触发事件
     */
    suspend fun trigger(triggerId: String) {
        val eventFactory = triggers[triggerId] ?: return
        val event = eventFactory()
        emit(event)
    }
}

/**
 * 发射器统计信息实现
 */
private class EmitterStatisticsImpl {
    private var totalEmitted = 0L
    private var successCount = 0L
    private var errorCount = 0L
    private var lastEmittedAt: Instant? = null
    private var totalEmitTime = 0L
    private var emitCount = 0L
    private val mutex = Mutex()
    
    suspend fun recordSuccess() {
        mutex.withLock {
            totalEmitted++
            successCount++
            lastEmittedAt = Instant.now()
        }
    }
    
    suspend fun recordError() {
        mutex.withLock {
            totalEmitted++
            errorCount++
        }
    }
    
    suspend fun updateEmitTime(timeMs: Long) {
        mutex.withLock {
            totalEmitTime += timeMs
            emitCount++
        }
    }
    
    suspend fun getStatistics(): EmitterStatistics {
        return mutex.withLock {
            EmitterStatistics(
                totalEmitted = totalEmitted,
                successCount = successCount,
                errorCount = errorCount,
                lastEmittedAt = lastEmittedAt,
                averageEmitTimeMs = if (emitCount > 0) {
                    totalEmitTime.toDouble() / emitCount
                } else 0.0
            )
        }
    }
}

/**
 * 事件发射器工厂
 */
object EventEmitterFactory {
    fun createEventBusEmitter(
        id: String,
        name: String,
        eventBus: EventBus,
        type: EmitterType = EmitterType.MANUAL
    ): EventEmitter = EventBusEmitter(id, name, type, eventBus)
    
    fun createScheduledEmitter(
        id: String,
        name: String,
        eventBus: EventBus,
        intervalMs: Long,
        eventFactory: suspend () -> Event
    ): EventEmitter = ScheduledEmitter(id, name, eventBus, intervalMs, eventFactory)
    
    fun createReactiveEmitter(
        id: String,
        name: String,
        eventBus: EventBus
    ): EventEmitter = ReactiveEmitter(id, name, eventBus)
}
