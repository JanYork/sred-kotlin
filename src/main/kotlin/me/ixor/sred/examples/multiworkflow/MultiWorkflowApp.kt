package me.ixor.sred.examples.multiworkflow

import me.ixor.sred.*
import me.ixor.sred.examples.transfer.*
import me.ixor.sred.examples.registration.*
import me.ixor.sred.core.logger
import kotlinx.coroutines.*

/**
 * 多流程管理示例
 * 演示如何注册多个流程、切换流程和刷新流程
 */
fun runMultiWorkflow(args: Array<String>) {
    runBlocking {
        val log = logger<Unit>()
        
        log.info { "=== 多流程管理示例 ===" }
        
        // 1. 创建引擎（使用转账流程作为默认流程）
        val engine = SRED.fromConfig(
            configPath = "transfer.json",
            dbPath = "multi_workflow.db",
            handlers = TransferService(
                TransferFixtures.createDefault().users,
                TransferFixtures.createDefault().accounts
            )
        )
        
        log.info { "已创建引擎，默认流程: ${engine.getCurrentWorkflowId()}" }
        
        // 2. 注册用户注册流程
        val registrationContext = RegistrationFixtures.createContext()
        engine.addHandler(RegistrationService(registrationContext.users, registrationContext.verificationCodes))
        
        engine.registerWorkflow(
            workflowId = "registration",
            configPath = "registration.json"
        )
        
        log.info { "已注册流程: ${engine.getWorkflowIds()}" }
        
        // 3. 在默认流程（转账）中启动一个实例
        log.info { "\n--- 执行转账流程 ---" }
        val transferResult = engine.runWorkflow(
            initialData = mapOf(
                "fromUserId" to "userA",
                "toUserId" to "userB",
                "amount" to 200.0
            ),
            instanceId = "transfer_001"
        )
        log.info { "转账完成，最终状态: ${runBlocking { transferResult.state() }}" }
        
        // 4. 切换到用户注册流程并执行
        log.info { "\n--- 切换到用户注册流程 ---" }
        engine.switchWorkflow("registration")
        
        val registrationResult = engine.runWorkflow(
            initialData = mapOf(
                "username" to "newuser",
                "email" to "newuser@example.com",
                "password" to "password123"
            ),
            instanceId = "registration_001"
        )
        log.info { "注册完成，最终状态: ${runBlocking { registrationResult.state() }}" }
        
        // 5. 演示流程刷新功能
        log.info { "\n--- 刷新转账流程 ---" }
        engine.switchWorkflow("default")
        
        // 模拟更新配置文件后刷新流程
        engine.refreshWorkflow(
            workflowId = "default",
            configPath = "sred.json"  // 可以指向更新后的配置文件
        )
        
        log.info { "流程已刷新，当前流程: ${engine.getCurrentWorkflowId()}" }
        
        // 6. 在新流程中启动实例
        val newTransferResult = engine.runWorkflow(
            initialData = mapOf(
                "fromUserId" to "userA",
                "toUserId" to "userB",
                "amount" to 100.0
            ),
            instanceId = "transfer_002"
        )
        log.info { "新转账完成，最终状态: ${runBlocking { newTransferResult.state() }}" }
        
        engine.close()
        log.info { "\n=== 示例完成 ===" }
    }
}

