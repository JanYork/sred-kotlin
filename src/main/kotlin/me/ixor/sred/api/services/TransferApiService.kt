package me.ixor.sred.api.services

import me.ixor.sred.*
import me.ixor.sred.core.getLocalState
import me.ixor.sred.api.models.TransferRequest
import me.ixor.sred.api.models.TransferResponse
import me.ixor.sred.examples.transfer.*
import me.ixor.sred.core.logger
import kotlinx.coroutines.*
import org.springframework.stereotype.Service

/**
 * 转账 API 服务
 * 支持快速执行的同步模式和长时间停顿的异步模式
 */
@Service
class TransferApiService {
    private val log = logger<TransferApiService>()
    private val context = TransferFixtures.createDefault()
    private val service = TransferService(context.users, context.accounts)
    private var engine: SREDEngine? = null
    private val executor = WorkflowExecutor()
    
    init {
        runBlocking {
            engine = SRED.fromConfig(
                configPath = "sred.json",
                dbPath = "transfer_state.db",
                handlers = service
            )
            // 注册引擎到执行器，用于超时检查
            executor.registerEngine("transfer", engine!!, instanceIdPrefix = "transfer_")
            
            // 启动时恢复暂停的实例
            executor.restorePausedInstances(engine!!)
        }
    }
    
    /**
     * 执行转账（同步模式，快速完成）
     */
    suspend fun executeTransfer(request: TransferRequest): TransferResponse {
        val engine = this.engine ?: throw IllegalStateException("引擎未初始化")
        
        return try {
            val instanceId = "transfer_${System.currentTimeMillis()}"
            engine.start(instanceId, mapOf(
                "fromUserId" to request.fromUserId,
                "toUserId" to request.toUserId,
                "amount" to request.amount
            ))
            
            engine.runUntilComplete(
                instanceId = instanceId,
                eventType = "process",
                eventName = "处理",
                onStateChange = { from, to ->
                    log.debug { "状态: ${from ?: "初始"} -> $to" }
                },
                onComplete = { state, _ ->
                    log.info { "✅ 完成: $state" }
                }
            )
            
            val finalState = engine.getCurrentState(instanceId)
            val transferId: String? = engine.getDataTyped<String>(instanceId, "transferId")
            
            TransferResponse(
                success = finalState?.contains("success") == true,
                instanceId = instanceId,
                finalState = finalState,
                transferId = transferId,
                message = if (finalState?.contains("success") == true) "转账成功" else "转账失败"
            )
        } catch (e: Exception) {
            log.error(e) { "转账失败" }
            TransferResponse(
                success = false,
                message = e.message ?: "转账失败"
            )
        }
    }
    
    /**
     * 启动转账流程（异步模式，立即返回）
     * 适用于需要审批等长时间停顿的场景
     */
    suspend fun startTransfer(request: TransferRequest): TransferResponse {
        val engine = this.engine ?: throw IllegalStateException("引擎未初始化")
        
        return try {
            val instanceId = "transfer_${System.currentTimeMillis()}"
            engine.start(instanceId, mapOf(
                "fromUserId" to request.fromUserId,
                "toUserId" to request.toUserId,
                "amount" to request.amount
            ))
            
            // 异步执行，在需要审批的状态暂停
            executor.executeAsync(
                engine = engine,
                instanceId = instanceId,
                autoProcess = true,  // 自动处理到完成或暂停状态
                onStateChange = { from, to ->
                    log.info { "转账流程状态: ${from ?: "初始"} -> $to" }
                },
                onComplete = { state ->
                    log.info { "转账流程完成: $state" }
                }
            )
            
            val currentState = engine.getCurrentState(instanceId)
            
            TransferResponse(
                success = false,  // 初始状态
                instanceId = instanceId,
                finalState = currentState,
                message = "转账流程已启动，正在处理"
            )
        } catch (e: Exception) {
            log.error(e) { "启动转账流程失败" }
            TransferResponse(
                success = false,
                message = e.message ?: "启动转账流程失败"
            )
        }
    }
    
    /**
     * 获取转账状态（支持从持久化存储查询，即使实例不在内存中）
     */
    suspend fun getTransferStatus(instanceId: String): TransferResponse {
        val engine = this.engine ?: throw IllegalStateException("引擎未初始化")
        
        return try {
            // getCurrentState 和 getContext 会自动从持久化存储加载
            val currentState = engine.getCurrentState(instanceId)
            val context = engine.getContext(instanceId)
            val transferId: String? = context?.let { ctx ->
                ctx.getLocalState<String>("transferId") ?: engine.getDataTyped<String>(instanceId, "transferId")
            } ?: engine.getDataTyped<String>(instanceId, "transferId")
            
            TransferResponse(
                success = currentState?.contains("success") == true,
                instanceId = instanceId,
                finalState = currentState,
                transferId = transferId,
                message = when {
                    currentState == null -> "实例不存在"
                    currentState.contains("success") -> "转账成功"
                    currentState.contains("failed") -> "转账失败"
                    else -> "处理中"
                }
            )
        } catch (e: Exception) {
            log.error(e) { "查询状态失败" }
            TransferResponse(
                success = false,
                message = e.message ?: "查询失败"
            )
        }
    }
    
    /**
     * 继续执行转账流程（触发下一个状态转换）
     */
    suspend fun continueTransfer(instanceId: String, eventType: String = "process", payload: Map<String, Any> = emptyMap()): TransferResponse {
        val engine = this.engine ?: throw IllegalStateException("引擎未初始化")
        
        return try {
            val result = executor.triggerEvent(
                engine = engine,
                instanceId = instanceId,
                eventType = eventType,
                eventName = "继续处理",
                payload = payload
            )
            
            if (result.success) {
                // 继续执行
                executor.continueExecution(
                    engine = engine,
                    instanceId = instanceId,
                    onStateChange = { from, to ->
                        log.info { "转账流程状态: ${from ?: "初始"} -> $to" }
                    },
                    onComplete = { state ->
                        log.info { "转账流程完成: $state" }
                    }
                )
            }
            
            val finalState = engine.getCurrentState(instanceId)
            val transferId: String? = engine.getDataTyped<String>(instanceId, "transferId")
            
            TransferResponse(
                success = finalState?.contains("success") == true,
                instanceId = instanceId,
                finalState = finalState,
                transferId = transferId,
                message = if (result.success) "处理成功" else "处理失败: ${result.error?.message}"
            )
        } catch (e: Exception) {
            log.error(e) { "继续转账流程失败" }
            TransferResponse(
                success = false,
                instanceId = instanceId,
                message = e.message ?: "继续转账流程失败"
            )
        }
    }
    
    /**
     * 获取所有暂停的实例
     */
    fun getPausedInstances(): List<WorkflowExecutor.PausedInstanceInfo> {
        return executor.getPausedInstances()
    }
    
    
    /**
     * 关闭服务
     */
    suspend fun close() {
        executor.close()
        engine?.close()
    }
}

