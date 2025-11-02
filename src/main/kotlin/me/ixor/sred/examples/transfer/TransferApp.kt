package me.ixor.sred.examples.transfer

import me.ixor.sred.*
import me.ixor.sred.core.logger
import kotlinx.coroutines.*

/**
 * 转账应用 - 应用程序入口和编排
 */
fun runTransfer(args: Array<String>) {
    runBlocking {
        val configPath = args.getOrNull(0) ?: "sred.json"
        val dbPath = "transfer_state.db"
        
        // 初始化测试数据
        val context = TransferFixtures.createDefault()
        
        // 显示初始状态
        TransferView.showInitialBalance(context.accounts)
        
        // 执行转账工作流
        val result = executeWorkflow(
            configPath = configPath,
            dbPath = dbPath,
            context = context,
            fromUserId = "userA",
            toUserId = "userB",
            amount = 200.0
        )
        
        // 显示最终结果
        TransferView.showFinalBalance(context.accounts)
        result?.let { r ->
            runBlocking {
                val transferId: String? = r.data("transferId")
                TransferView.showTransferId(transferId)
            }
        }
    }
}

/**
 * 执行转账工作流
 */
private suspend fun executeWorkflow(
    configPath: String,
    dbPath: String,
    context: TransferContext,
    fromUserId: String,
    toUserId: String,
    amount: Double
): WorkflowResult? {
    val log = logger<Unit>()
    val service = TransferService(context.users, context.accounts)
    
    return SRED.fromConfig(
        configPath = configPath,
        dbPath = dbPath,
        handlers = service
    ).use { engine ->
        // 启动工作流
        val instanceId = "transfer_${System.currentTimeMillis()}"
        engine.start(instanceId, mapOf(
            "fromUserId" to fromUserId,
            "toUserId" to toUserId,
            "amount" to amount
        ))
        
        // 执行工作流直到完成
        engine.runUntilComplete(
            instanceId = instanceId,
            eventType = "process",
            eventName = "处理",
            onStateChange = { from, to ->
                log.info { "状态: ${from ?: "初始"} -> $to" }
            },
            onComplete = { state, _ ->
                log.info { "✅ 完成: $state" }
            }
        )
        
        val finalState = engine.getCurrentState(instanceId)
        if (finalState != null) {
            WorkflowResult(instanceId, finalState, engine)
        } else {
            null
        }
    }
}

