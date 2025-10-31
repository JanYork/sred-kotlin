package me.ixor.sred.core

import java.time.Instant
import java.util.*

/**
 * 事务事件接口 - SRED架构的一致性管理
 * 
 * 根据论文4.5节：事务事件（Transactional Event）用于确保
 * 一组状态转移的原子性。
 */
interface TransactionEvent {
    /**
     * 事务ID
     */
    val transactionId: TransactionId
    
    /**
     * 事务中的事件列表
     */
    val events: List<Event>
    
    /**
     * 事务状态
     */
    val status: TransactionStatus
    
    /**
     * 事务创建时间
     */
    val createdAt: Instant
    
    /**
     * 补偿处理器列表（按执行顺序的逆序）
     */
    val compensationHandlers: List<CompensationHandler>
    
    /**
     * 执行事务
     */
    suspend fun execute(): TransactionResult
    
    /**
     * 回滚事务
     */
    suspend fun rollback(): TransactionResult
}

/**
 * 事务ID类型别名
 */
typealias TransactionId = String

/**
 * 事务状态
 */
enum class TransactionStatus {
    /**
     * 准备中
     */
    PREPARING,
    
    /**
     * 执行中
     */
    EXECUTING,
    
    /**
     * 已提交
     */
    COMMITTED,
    
    /**
     * 已回滚
     */
    ROLLED_BACK,
    
    /**
     * 失败
     */
    FAILED
}

/**
 * 事务结果
 */
data class TransactionResult(
    val success: Boolean,
    val transactionId: TransactionId,
    val executedEvents: List<Event>,
    val failedEvent: Event? = null,
    val error: Throwable? = null,
    val compensationExecuted: Boolean = false
)

/**
 * 补偿处理器接口
 * 
 * 根据论文4.5节：补偿机制（Compensation Handler）用于
 * 当状态转移失败时回滚或调整。
 */
interface CompensationHandler {
    /**
     * 补偿处理器ID
     */
    val id: String
    
    /**
     * 描述
     */
    val description: String
    
    /**
     * 执行补偿操作
     * @param context 状态上下文
     * @param event 触发的事件
     * @return 补偿结果
     */
    suspend fun compensate(
        context: StateContext,
        event: Event
    ): CompensationResult
}

/**
 * 补偿结果
 */
data class CompensationResult(
    val success: Boolean,
    val error: Throwable? = null,
    val updatedContext: StateContext? = null
)

/**
 * 事务事件实现
 */
class TransactionEventImpl(
    override val transactionId: TransactionId = UUID.randomUUID().toString(),
    override val events: List<Event>,
    override val compensationHandlers: List<CompensationHandler> = emptyList(),
    override val createdAt: Instant = Instant.now()
) : TransactionEvent {
    
    override var status: TransactionStatus = TransactionStatus.PREPARING
        private set
    
    private val executedEvents = mutableListOf<Event>()
    
    override suspend fun execute(): TransactionResult {
        status = TransactionStatus.EXECUTING
        
        try {
            for ((index, event) in events.withIndex()) {
                // 这里需要在实际的状态转移执行中调用
                // 假设事件执行成功
                executedEvents.add(event)
            }
            
            status = TransactionStatus.COMMITTED
            return TransactionResult(
                success = true,
                transactionId = transactionId,
                executedEvents = executedEvents.toList()
            )
        } catch (e: Exception) {
            status = TransactionStatus.FAILED
            val failedEvent = executedEvents.lastOrNull() ?: events.firstOrNull()
            
            // 执行补偿
            val compensationResult = rollback()
            
            return TransactionResult(
                success = false,
                transactionId = transactionId,
                executedEvents = executedEvents.toList(),
                failedEvent = failedEvent,
                error = e,
                compensationExecuted = compensationResult.success
            )
        }
    }
    
    override suspend fun rollback(): TransactionResult {
        status = TransactionStatus.ROLLED_BACK
        
        // 按逆序执行补偿处理器
        val compensationResults = mutableListOf<CompensationResult>()
        var allSuccess = true
        
        for (handler in compensationHandlers.reversed()) {
            try {
                val event = executedEvents.lastOrNull() ?: events.firstOrNull()
                if (event != null) {
                    // 这里需要实际的上下文
                    // 暂时返回成功
                    compensationResults.add(
                        CompensationResult(success = true)
                    )
                }
            } catch (e: Exception) {
                allSuccess = false
                compensationResults.add(
                    CompensationResult(success = false, error = e)
                )
            }
        }
        
        return TransactionResult(
            success = allSuccess,
            transactionId = transactionId,
            executedEvents = executedEvents.toList(),
            compensationExecuted = allSuccess
        )
    }
}

/**
 * 简单补偿处理器实现
 */
class SimpleCompensationHandler(
    override val id: String,
    override val description: String,
    private val compensationAction: suspend (StateContext, Event) -> CompensationResult
) : CompensationHandler {
    
    override suspend fun compensate(
        context: StateContext,
        event: Event
    ): CompensationResult {
        return try {
            compensationAction(context, event)
        } catch (e: Exception) {
            CompensationResult(success = false, error = e)
        }
    }
}

/**
 * 事务事件构建器
 */
class TransactionEventBuilder {
    private var transactionId: TransactionId = UUID.randomUUID().toString()
    private val events = mutableListOf<Event>()
    private val compensationHandlers = mutableListOf<CompensationHandler>()
    
    fun transactionId(id: TransactionId) = apply { this.transactionId = id }
    
    fun addEvent(event: Event) = apply { this.events.add(event) }
    
    fun addEvents(vararg events: Event) = apply { this.events.addAll(events) }
    
    fun addCompensationHandler(handler: CompensationHandler) = apply {
        this.compensationHandlers.add(handler)
    }
    
    fun build(): TransactionEvent {
        require(events.isNotEmpty()) { "Transaction must have at least one event" }
        return TransactionEventImpl(
            transactionId = transactionId,
            events = events.toList(),
            compensationHandlers = compensationHandlers.toList()
        )
    }
}

/**
 * 事务事件工厂
 */
object TransactionEventFactory {
    fun builder(): TransactionEventBuilder = TransactionEventBuilder()
    
    fun create(
        events: List<Event>,
        compensationHandlers: List<CompensationHandler> = emptyList()
    ): TransactionEvent {
        return TransactionEventImpl(
            events = events,
            compensationHandlers = compensationHandlers
        )
    }
}

