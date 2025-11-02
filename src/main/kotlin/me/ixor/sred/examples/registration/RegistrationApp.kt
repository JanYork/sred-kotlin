package me.ixor.sred.examples.registration

import me.ixor.sred.*
import me.ixor.sred.core.logger
import kotlinx.coroutines.*

/**
 * 用户注册应用 - 应用程序入口和编排
 */
fun runRegistration(args: Array<String>) {
    runBlocking {
        val configPath = args.getOrNull(0) ?: "registration.json"
        val dbPath = "registration_state.db"
        
        // 初始化测试数据
        val context = RegistrationFixtures.createContext()
        
        // 执行注册工作流
        val result = executeWorkflow(
            configPath = configPath,
            dbPath = dbPath,
            context = context,
            username = "testuser",
            email = "test@example.com",
            password = "password123",
            verificationCode = "123456" // 模拟用户输入的验证码
        )
        
        // 显示结果
        result?.let { r ->
            val userId: String? = runBlocking { r.data<String>("userId") }
            val success = runBlocking { r.state()?.contains("success") == true }
            val username: String? = runBlocking { r.data<String>("username") }
            username?.let { RegistrationView.showResult(it, success) }
            userId?.let { 
                val log = logger<Unit>()
                log.info { "用户ID: $it" }
            }
        }
    }
}

/**
 * 执行用户注册工作流
 */
private suspend fun executeWorkflow(
    configPath: String,
    dbPath: String,
    context: RegistrationContext,
    username: String,
    email: String,
    password: String,
    verificationCode: String
): WorkflowResult? {
    val log = logger<Unit>()
    val service = RegistrationService(context.users, context.verificationCodes)
    
    // 显示注册信息
    RegistrationView.showRegistrationInfo(username, email)
    
    return SRED.fromConfig(
        configPath = configPath,
        dbPath = dbPath,
        handlers = service
    ).use { engine ->
        // 启动工作流
        val instanceId = "registration_${System.currentTimeMillis()}"
        engine.start(instanceId, mapOf(
            "username" to username,
            "email" to email,
            "password" to password
        ))
        
        // 执行第一阶段：到等待验证状态
        var emailSent = false
        engine.runUntilComplete(
            instanceId = instanceId,
            eventType = "process",
            eventName = "处理",
            onStateChange = { from, to ->
                log.info { "状态: ${from ?: "初始"} -> $to" }
                
                // 在发送邮件后，显示验证码
                if (to == RegistrationStates.SENDING_EMAIL && !emailSent) {
                    emailSent = true
                }
            },
            onComplete = { state, _ ->
                if (state != RegistrationStates.WAITING_VERIFICATION) {
                    log.info { "✅ 完成: $state" }
                }
            }
        )
        
        // 在发送邮件后，显示验证码
        if (emailSent) {
            delay(300)
            val code = context.verificationCodes[email]
            code?.let {
                RegistrationView.showVerificationCode(email, it)
            }
        }
        
        // 如果到达等待验证状态，需要注入验证码后继续
        val currentState = engine.getCurrentState(instanceId)
        if (currentState == RegistrationStates.WAITING_VERIFICATION) {
            delay(500) // 模拟用户等待
            val code = context.verificationCodes[email] ?: verificationCode
            if (!emailSent) {
                RegistrationView.showVerificationCode(email, code)
            }
            engine.process(instanceId, "verify", "验证验证码", mapOf("inputCode" to code))
            
            // 继续执行后续流程
            engine.runUntilComplete(
                instanceId = instanceId,
                eventType = "process",
                eventName = "继续处理",
                onStateChange = { from, to ->
                    log.info { "状态: ${from ?: "初始"} -> $to" }
                },
                onComplete = { state, _ ->
                    log.info { "✅ 完成: $state" }
                }
            )
        }
        
        val finalState = engine.getCurrentState(instanceId)
        if (finalState != null) {
            WorkflowResult(instanceId, finalState, engine)
        } else {
            null
        }
    }
}

