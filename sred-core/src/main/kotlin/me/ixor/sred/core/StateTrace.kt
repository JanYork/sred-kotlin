package me.ixor.sred.core

import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * 状态轨迹追踪 - SRED架构的可观测性核心
 * 
 * 根据论文5.2节：状态迁移轨迹形成完整的可追溯时间线。
 */
interface StateTrace {
    /**
     * 轨迹ID
     */
    val traceId: TraceId
    
    /**
     * 实体ID（如实例ID）
     */
    val entityId: String
    
    /**
     * 轨迹条目列表
     */
    val entries: List<TraceEntry>
    
    /**
     * 轨迹开始时间
     */
    val startTime: Instant
    
    /**
     * 轨迹结束时间
     */
    val endTime: Instant?
    
    /**
     * 添加轨迹条目
     */
    suspend fun addEntry(entry: TraceEntry)
    
    /**
     * 获取完整轨迹时间线
     */
    fun getTimeline(): List<TraceEntry>
    
    /**
     * 获取状态转换序列
     */
    fun getStateTransitions(): List<StateTransitionTrace>
    
    /**
     * 获取事件序列
     */
    fun getEventSequence(): List<EventTrace>
    
    /**
     * 分析轨迹性能指标
     */
    fun analyzePerformance(): TracePerformanceMetrics
}

/**
 * 轨迹ID类型别名
 */
typealias TraceId = String

/**
 * 轨迹条目
 */
sealed class TraceEntry {
    /**
     * 条目ID
     */
    abstract val id: String
    
    /**
     * 时间戳
     */
    abstract val timestamp: Instant
    
    /**
     * 条目类型
     */
    abstract val type: TraceEntryType
}

/**
 * 轨迹条目类型
 */
enum class TraceEntryType {
    STATE_ENTER,
    STATE_EXIT,
    EVENT_RECEIVED,
    EVENT_PROCESSED,
    TRANSITION_START,
    TRANSITION_COMPLETE,
    TRANSITION_FAILED,
    ERROR,
    COMPENSATION_START,
    COMPENSATION_COMPLETE
}

/**
 * 状态进入轨迹条目
 */
data class StateEnterEntry(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Instant = Instant.now(),
    val stateId: StateId,
    val contextId: ContextId,
    val triggeredByEvent: EventId? = null
) : TraceEntry() {
    override val type: TraceEntryType = TraceEntryType.STATE_ENTER
}

/**
 * 状态退出轨迹条目
 */
data class StateExitEntry(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Instant = Instant.now(),
    val stateId: StateId,
    val contextId: ContextId,
    val duration: Duration? = null
) : TraceEntry() {
    override val type: TraceEntryType = TraceEntryType.STATE_EXIT
}

/**
 * 事件接收轨迹条目
 */
data class EventReceivedEntry(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Instant = Instant.now(),
    val event: Event,
    val currentStateId: StateId?
) : TraceEntry() {
    override val type: TraceEntryType = TraceEntryType.EVENT_RECEIVED
}

/**
 * 状态转移轨迹条目
 */
data class StateTransitionTrace(
    val fromStateId: StateId?,
    val toStateId: StateId,
    val eventId: EventId,
    val timestamp: Instant,
    val duration: Duration?,
    val success: Boolean,
    val error: Throwable? = null
)

/**
 * 事件轨迹
 */
data class EventTrace(
    val eventId: EventId,
    val eventType: EventType,
    val receivedAt: Instant,
    val processedAt: Instant?,
    val duration: Duration?,
    val success: Boolean
)

/**
 * 轨迹性能指标
 */
data class TracePerformanceMetrics(
    val totalTransitions: Int,
    val averageTransitionTimeMs: Double,
    val fastestTransitionMs: Long,
    val slowestTransitionMs: Long,
    val totalEventsProcessed: Int,
    val averageEventProcessingTimeMs: Double,
    val stateVisitCounts: Map<StateId, Int>,
    val errorCount: Int,
    val totalDurationMs: Long
)

/**
 * 状态轨迹实现
 */
class StateTraceImpl(
    override val traceId: TraceId = UUID.randomUUID().toString(),
    override val entityId: String,
    override val startTime: Instant = Instant.now()
) : StateTrace {
    
    private val _entries = mutableListOf<TraceEntry>()
    override val entries: List<TraceEntry> get() = _entries.toList()
    
    override var endTime: Instant? = null
        private set
    
    override suspend fun addEntry(entry: TraceEntry) {
        _entries.add(entry)
        if (entry.type == TraceEntryType.STATE_EXIT && endTime == null) {
            // 可以根据业务逻辑判断轨迹是否结束
        }
    }
    
    override fun getTimeline(): List<TraceEntry> {
        return entries.sortedBy { it.timestamp }
    }
    
    override fun getStateTransitions(): List<StateTransitionTrace> {
        val transitions = mutableListOf<StateTransitionTrace>()
        val enterEntries = entries.filterIsInstance<StateEnterEntry>()
        val exitEntries = entries.filterIsInstance<StateExitEntry>()
        
        for (i in 1 until enterEntries.size) {
            val prevEnter = enterEntries[i - 1]
            val currEnter = enterEntries[i]
            
            val correspondingExit = exitEntries.findLast { 
                it.stateId == prevEnter.stateId && 
                it.timestamp.isBefore(currEnter.timestamp) 
            }
            
            transitions.add(
                StateTransitionTrace(
                    fromStateId = prevEnter.stateId,
                    toStateId = currEnter.stateId,
                    eventId = currEnter.triggeredByEvent ?: "unknown",
                    timestamp = currEnter.timestamp,
                    duration = if (correspondingExit != null) {
                        Duration.between(correspondingExit.timestamp, currEnter.timestamp)
                    } else null,
                    success = true
                )
            )
        }
        
        return transitions
    }
    
    override fun getEventSequence(): List<EventTrace> {
        val receivedEvents = entries.filterIsInstance<EventReceivedEntry>()
        val eventTraces = mutableListOf<EventTrace>()
        
        for (entry in receivedEvents) {
            // 查找对应的处理完成时间
            val processedAt = entries
                .filter { 
                    it.timestamp.isAfter(entry.timestamp) && 
                    (it.type == TraceEntryType.TRANSITION_COMPLETE || 
                     it.type == TraceEntryType.TRANSITION_FAILED)
                }
                .minByOrNull { it.timestamp }
                ?.timestamp
            
            eventTraces.add(
                EventTrace(
                    eventId = entry.event.id,
                    eventType = entry.event.type,
                    receivedAt = entry.timestamp,
                    processedAt = processedAt,
                    duration = processedAt?.let { Duration.between(entry.timestamp, it) },
                    success = processedAt != null
                )
            )
        }
        
        return eventTraces
    }
    
    override fun analyzePerformance(): TracePerformanceMetrics {
        val transitions = getStateTransitions()
        val events = getEventSequence()
        
        val transitionTimes = transitions.mapNotNull { 
            it.duration?.toMillis() 
        }
        
        val eventTimes = events.mapNotNull { 
            it.duration?.toMillis() 
        }
        
        val stateVisitCounts = entries
            .filterIsInstance<StateEnterEntry>()
            .groupingBy { it.stateId }
            .eachCount()
        
        val errorCount = entries.count { 
            it.type == TraceEntryType.ERROR || 
            it.type == TraceEntryType.TRANSITION_FAILED 
        }
        
        val totalDuration = if (endTime != null) {
            Duration.between(startTime, endTime).toMillis()
        } else {
            Duration.between(startTime, Instant.now()).toMillis()
        }
        
        return TracePerformanceMetrics(
            totalTransitions = transitions.size,
            averageTransitionTimeMs = if (transitionTimes.isNotEmpty()) {
                transitionTimes.average()
            } else 0.0,
            fastestTransitionMs = transitionTimes.minOrNull() ?: 0L,
            slowestTransitionMs = transitionTimes.maxOrNull() ?: 0L,
            totalEventsProcessed = events.size,
            averageEventProcessingTimeMs = if (eventTimes.isNotEmpty()) {
                eventTimes.average()
            } else 0.0,
            stateVisitCounts = stateVisitCounts,
            errorCount = errorCount,
            totalDurationMs = totalDuration
        )
    }
    
    fun finish() {
        endTime = Instant.now()
    }
}

/**
 * 轨迹追踪器接口
 */
interface TraceCollector {
    /**
     * 开始追踪
     */
    suspend fun startTrace(entityId: String): StateTrace
    
    /**
     * 获取轨迹
     */
    fun getTrace(traceId: TraceId): StateTrace?
    
    /**
     * 获取实体的所有轨迹
     */
    fun getTracesForEntity(entityId: String): List<StateTrace>
    
    /**
     * 添加轨迹条目
     */
    suspend fun addTraceEntry(traceId: TraceId, entry: TraceEntry)
    
    /**
     * 结束追踪
     */
    suspend fun finishTrace(traceId: TraceId)
    
    /**
     * 获取所有轨迹
     */
    fun getAllTraces(): List<StateTrace>
}

/**
 * 轨迹追踪器实现
 */
class TraceCollectorImpl : TraceCollector {
    private val traces = mutableMapOf<TraceId, StateTrace>()
    private val entityTraces = mutableMapOf<String, MutableList<TraceId>>()
    
    override suspend fun startTrace(entityId: String): StateTrace {
        val trace = StateTraceImpl(entityId = entityId)
        traces[trace.traceId] = trace
        entityTraces.getOrPut(entityId) { mutableListOf() }.add(trace.traceId)
        return trace
    }
    
    override fun getTrace(traceId: TraceId): StateTrace? {
        return traces[traceId]
    }
    
    override fun getTracesForEntity(entityId: String): List<StateTrace> {
        val traceIds = entityTraces[entityId] ?: return emptyList()
        return traceIds.mapNotNull { traces[it] }
    }
    
    override suspend fun addTraceEntry(traceId: TraceId, entry: TraceEntry) {
        val trace = traces[traceId] as? StateTraceImpl ?: return
        trace.addEntry(entry)
    }
    
    override suspend fun finishTrace(traceId: TraceId) {
        val trace = traces[traceId] as? StateTraceImpl ?: return
        trace.finish()
    }
    
    override fun getAllTraces(): List<StateTrace> {
        return traces.values.toList()
    }
}

/**
 * 轨迹追踪器工厂
 */
object TraceCollectorFactory {
    fun create(): TraceCollector = TraceCollectorImpl()
}

