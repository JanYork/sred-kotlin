package me.ixor.sred.event

import me.ixor.sred.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 事件总线 - SRED架构的事件层核心
 * 
 * 事件总线负责事件的发布、分发、过滤与订阅机制。
 * 它是系统的神经通路，决定系统何时触发状态轮转。
 */
interface EventBus {
    /**
     * 发布事件
     */
    suspend fun publish(event: Event)
    
    /**
     * 订阅事件
     */
    fun subscribe(
        eventType: EventType,
        listener: EventListener,
        filter: EventFilter? = null
    ): Subscription
    
    /**
     * 取消订阅
     */
    fun unsubscribe(subscription: Subscription)
    
    /**
     * 获取事件统计信息
     */
    fun getStatistics(): EventBusStatistics
    
    /**
     * 启动事件总线
     */
    suspend fun start()
    
    /**
     * 停止事件总线
     */
    suspend fun stop()
}

/**
 * 事件监听器接口
 */
interface EventListener {
    /**
     * 监听器ID
     */
    val id: String
    
    /**
     * 处理事件
     */
    suspend fun onEvent(event: Event)
    
    /**
     * 处理错误
     */
    suspend fun onError(event: Event, error: Throwable)
}

/**
 * 事件过滤器接口
 */
interface EventFilter {
    /**
     * 过滤事件
     */
    fun filter(event: Event): Boolean
}

/**
 * 订阅信息
 */
data class Subscription(
    val id: String,
    val eventType: EventType,
    val listener: EventListener,
    val filter: EventFilter?,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 事件总线统计信息
 */
data class EventBusStatistics(
    val totalEventsPublished: Long,
    val totalEventsProcessed: Long,
    val activeSubscriptions: Int,
    val errorCount: Long,
    val averageProcessingTimeMs: Double
)

/**
 * 事件总线实现
 */
class EventBusImpl(
    private val maxConcurrency: Int = 10,
    private val bufferSize: Int = 1000
) : EventBus {
    
    private val eventChannel = Channel<Event>(UNLIMITED)
    private val subscriptions = ConcurrentHashMap<EventType, CopyOnWriteArrayList<Subscription>>()
    private val statistics = EventBusStatisticsImpl()
    private val mutex = Mutex()
    private var isRunning = false
    private var processingJob: Job? = null
    private var processingScope: CoroutineScope? = null  // 统一的协程作用域
    
    override suspend fun publish(event: Event) {
        if (!isRunning) {
            throw IllegalStateException("EventBus is not running")
        }
        
        eventChannel.send(event)
        statistics.incrementPublished()
    }
    
    override fun subscribe(
        eventType: EventType,
        listener: EventListener,
        filter: EventFilter?
    ): Subscription {
        val subscription = Subscription(
            id = UUID.randomUUID().toString(),
            eventType = eventType,
            listener = listener,
            filter = filter
        )
        
        subscriptions.computeIfAbsent(eventType) { CopyOnWriteArrayList() }
            .add(subscription)
        
        return subscription
    }
    
    override fun unsubscribe(subscription: Subscription) {
        subscriptions[subscription.eventType]?.remove(subscription)
    }
    
    override fun getStatistics(): EventBusStatistics {
        // 注意：使用 runBlocking 是因为接口是同步的，但内部统计使用 Mutex（需要 suspend）
        // 这可能会阻塞调用线程，但通常统计方法调用频率较低，可以接受
        // 如果需要高性能，可以考虑将接口改为 suspend 函数
        return runBlocking { statistics.getStatistics() }
    }
    
    override suspend fun start() {
        mutex.withLock {
            if (isRunning) return
            
            isRunning = true
            // 创建统一的协程作用域
            processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            processingJob = processingScope!!.launch {
                processEvents()
            }
        }
    }
    
    override suspend fun stop() {
        mutex.withLock {
            if (!isRunning) return
            
            isRunning = false
            
            // 关闭 channel（这会停止接收新事件）
            eventChannel.close()
            
            // 取消处理任务和作用域
            processingScope?.cancel()
            
            // 等待处理任务完成（但不等待未完成的事件）
            processingJob?.join()
            
            processingScope = null
            processingJob = null
        }
    }
    
    private suspend fun processEvents() {
        val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrency)
        val scope = processingScope ?: return
        
        try {
            // 使用 consumeEach 或 for 循环处理事件
            // 当 channel 关闭时，for 循环会正常退出
            for (event in eventChannel) {
                semaphore.acquire()
                scope.launch {
                    try {
                        processEvent(event)
                    } catch (e: CancellationException) {
                        // 取消异常，重新抛出以便协程正确处理
                        throw e
                    } catch (e: Exception) {
                        // 记录错误但继续处理
                        statistics.incrementError()
                    } finally {
                        semaphore.release()
                    }
                }
            }
        } catch (e: CancellationException) {
            // 任务被取消，正常退出
            throw e
        } catch (e: Exception) {
            // Channel 关闭时会抛出 ClosedReceiveChannelException，这是正常的
            // 其他异常记录日志
            if (e !is kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                // 记录非预期的错误
            }
        }
    }
    
    private suspend fun processEvent(event: Event) {
        val startTime = System.currentTimeMillis()
        
        try {
            val eventSubscriptions = subscriptions[event.type] ?: return
            
            for (subscription in eventSubscriptions) {
                try {
                    if (subscription.filter?.filter(event) != false) {
                        // 添加超时机制，最多等待5秒
                        withTimeout(5000) {
                            subscription.listener.onEvent(event)
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    subscription.listener.onError(event, e)
                    statistics.incrementError()
                } catch (e: Exception) {
                    subscription.listener.onError(event, e)
                    statistics.incrementError()
                }
            }
            
            statistics.incrementProcessed()
            statistics.updateProcessingTime(System.currentTimeMillis() - startTime)
            
        } catch (e: Exception) {
            statistics.incrementError()
            throw e
        }
    }
}

/**
 * 事件总线统计信息实现
 */
private class EventBusStatisticsImpl {
    private var totalPublished = 0L
    private var totalProcessed = 0L
    private var errorCount = 0L
    private var totalProcessingTime = 0L
    private var processingCount = 0L
    private val mutex = Mutex()
    
    suspend fun incrementPublished() {
        mutex.withLock { totalPublished++ }
    }
    
    suspend fun incrementProcessed() {
        mutex.withLock { totalProcessed++ }
    }
    
    suspend fun incrementError() {
        mutex.withLock { errorCount++ }
    }
    
    suspend fun updateProcessingTime(timeMs: Long) {
        mutex.withLock {
            totalProcessingTime += timeMs
            processingCount++
        }
    }
    
    suspend fun getStatistics(): EventBusStatistics {
        return mutex.withLock {
            EventBusStatistics(
                totalEventsPublished = totalPublished,
                totalEventsProcessed = totalProcessed,
                activeSubscriptions = 0, // Will be calculated by EventBus
                errorCount = errorCount,
                averageProcessingTimeMs = if (processingCount > 0) {
                    totalProcessingTime.toDouble() / processingCount
                } else 0.0
            )
        }
    }
}

/**
 * 简单事件过滤器实现
 */
class SimpleEventFilter(
    private val predicate: (Event) -> Boolean
) : EventFilter {
    override fun filter(event: Event): Boolean = predicate(event)
}

/**
 * 事件类型过滤器
 */
class EventTypeFilter(
    private val allowedTypes: Set<EventType>
) : EventFilter {
    override fun filter(event: Event): Boolean = event.type in allowedTypes
}

/**
 * 事件优先级过滤器
 */
class EventPriorityFilter(
    private val minPriority: EventPriority
) : EventFilter {
    override fun filter(event: Event): Boolean = event.priority.level >= minPriority.level
}

/**
 * 事件总线工厂
 */
object EventBusFactory {
    fun create(
        maxConcurrency: Int = 10,
        bufferSize: Int = 1000
    ): EventBus = EventBusImpl(maxConcurrency, bufferSize)
}
