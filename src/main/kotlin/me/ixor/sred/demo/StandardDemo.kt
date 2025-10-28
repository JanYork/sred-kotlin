package me.ixor.sred.demo

import me.ixor.sred.core.*
import me.ixor.sred.declarative.*
import me.ixor.sred.declarative.format.*
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 标准演示
 * 展示SRED架构的核心功能和特性
 */
object StandardDemo {
    
    /**
     * 用户注册服务
     */
    class UserRegistrationService {
        
        suspend fun validateUser(context: StateContext): StateResult {
            val email = context.getLocalState("email", String::class.java)
            val password = context.getLocalState("password", String::class.java)
            val username = context.getLocalState("username", String::class.java)
            
            DemoFramework.info("验证用户信息: $email")
            delay(1000)
            
            val isValid = !email.isNullOrEmpty() && 
                         !password.isNullOrEmpty() && 
                         !username.isNullOrEmpty() &&
                         email.contains("@")
            
            return if (isValid) {
                DemoFramework.success("用户信息验证通过")
                StateResult.success(mapOf("validated" to true))
            } else {
                DemoFramework.error("用户信息验证失败")
                StateResult.failure("用户信息不完整或格式错误")
            }
        }
        
        suspend fun storeUser(context: StateContext): StateResult {
            val email = context.getLocalState("email", String::class.java)
            val username = context.getLocalState("username", String::class.java)
            
            DemoFramework.info("存储用户信息: $username")
            delay(1500)
            
            val userId = "user_${System.currentTimeMillis()}"
            DemoFramework.success("用户信息存储成功，用户ID: $userId")
            
            return StateResult.success(mapOf(
                "userId" to userId,
                "stored" to true
            ))
        }
        
        suspend fun sendEmailNotification(context: StateContext): StateResult {
            val email = context.getLocalState("email", String::class.java)
            
            DemoFramework.info("发送邮件通知: $email")
            delay(2000)
            
            DemoFramework.success("邮件通知发送完成")
            return StateResult.success(mapOf("emailSent" to true))
        }
    }
    
    /**
     * 运行标准演示
     */
    suspend fun runDemo(): DemoFramework.DemoResult {
        val config = DemoFramework.createConfig(
            name = "SRED架构标准演示",
            description = "展示状态轮转与事件驱动架构的核心功能和特性",
            category = "核心功能",
            timeout = 30000L
        )
        
        return DemoFramework.runDemo(config) {
            DemoFramework.step("创建用户注册状态流") {
                val stateFlow = StateFlow()
                
                // 定义状态
                stateFlow.state("user_unregistered", "用户未注册", StateFlow.StateType.INITIAL, isInitial = true)
                stateFlow.state("user_registering", "用户注册中", StateFlow.StateType.NORMAL)
                stateFlow.state("user_registered", "用户已注册", StateFlow.StateType.FINAL, isFinal = true)
                stateFlow.state("user_registration_failed", "用户注册失败", StateFlow.StateType.ERROR, isError = true)
                
                // 子状态
                stateFlow.state("user_validation", "用户信息验证", StateFlow.StateType.NORMAL, "user_registering")
                stateFlow.state("user_storage", "存储用户信息", StateFlow.StateType.NORMAL, "user_registering")
                stateFlow.state("email_notification", "发送邮件通知", StateFlow.StateType.NORMAL, "user_registered")
                
                // 定义转移
                stateFlow.transition("user_unregistered", "user_registering", StateFlow.TransitionCondition.Success)
                stateFlow.transition("user_registering", "user_registered", StateFlow.TransitionCondition.Success)
                stateFlow.transition("user_registering", "user_registration_failed", StateFlow.TransitionCondition.Failure)
                
                // 子状态转移
                stateFlow.transition("user_validation", "user_storage", StateFlow.TransitionCondition.Success)
                stateFlow.transition("user_validation", "user_registration_failed", StateFlow.TransitionCondition.Failure)
                stateFlow.transition("user_storage", "user_registered", StateFlow.TransitionCondition.Success)
                stateFlow.transition("user_storage", "user_registration_failed", StateFlow.TransitionCondition.Failure)
                stateFlow.transition("user_registered", "email_notification", StateFlow.TransitionCondition.Success)
                
                // 绑定服务函数
                val userService = UserRegistrationService()
                stateFlow.bindFunction("user_validation", userService::validateUser)
                stateFlow.bindFunction("user_storage", userService::storeUser)
                stateFlow.bindFunction("email_notification", userService::sendEmailNotification)
                
                DemoFramework.success("状态流创建完成")
                DemoFramework.info("包含 ${stateFlow.states.size} 个状态")
                DemoFramework.info("包含 ${stateFlow.transitions.size} 个转移")
            }
            
            DemoFramework.step("构建状态机") {
                val stateFlow = createUserRegistrationStateFlow()
                val stateMachine = stateFlow.build()
                val commandHandler = CommandHandler()
                commandHandler.registerStateMachine("user_registration", stateMachine)
                
                DemoFramework.success("状态机构建完成")
            }
            
            DemoFramework.step("处理用户注册命令") {
                val stateFlow = createUserRegistrationStateFlow()
                val stateMachine = stateFlow.build()
                val commandHandler = CommandHandler()
                commandHandler.registerStateMachine("user_registration", stateMachine)
                
                val users = listOf(
                    mapOf("email" to "user1@example.com", "password" to "password123", "username" to "user1"),
                    mapOf("email" to "user2@example.com", "password" to "password456", "username" to "user2"),
                    mapOf("email" to "invalid-email", "password" to "short", "username" to "user3")
                )
                
                users.forEachIndexed { index, userData ->
                    DemoFramework.info("处理用户注册 ${index + 1}: ${userData["email"]}")
                    
                    val command = Command(
                        type = "user_registration",
                        data = userData
                    )
                    
                    val result = commandHandler.handleCommand(command)
                    
                    if (result.success) {
                        DemoFramework.success("用户注册成功")
                    } else {
                        DemoFramework.error("用户注册失败: ${result.error?.message}")
                    }
                }
            }
            
            DemoFramework.step("展示状态流转图") {
                DemoFramework.info("用户注册状态流转图:")
                println("""
                    ┌─────────────────────────────────────────────────────────┐
                    │                    主状态流转                            │
                    │  用户未注册 → 用户注册中 → 用户已注册/失败                │
                    └─────────────────────────────────────────────────────────┘
                    
                    ┌─────────────────────────────────────────────────────────┐
                    │                  子状态流转 (用户注册中)                  │
                    │  用户信息验证 → 存储用户信息 → 用户已注册                │
                    │      ↓             ↓             ↓                    │
                    │     成功          成功          成功                    │
                    │      ↓             ↓             ↓                    │
                    │     失败          失败          失败                    │
                    │      ↓             ↓             ↓                    │
                    │  注册失败 ← 注册失败 ← 注册失败                        │
                    └─────────────────────────────────────────────────────────┘
                    
                    ┌─────────────────────────────────────────────────────────┐
                    │                子状态流转 (用户已注册)                    │
                    │  发送邮件通知 → 通知已发送                              │
                    └─────────────────────────────────────────────────────────┘
                """.trimIndent())
            }
            
            DemoFramework.step("展示架构特点") {
                DemoFramework.info("SRED架构特点:")
                println("""
                    ┌─────────────────────────────────────────────────────────┐
                    │                    核心特点                              │
                    ├─────────────────────────────────────────────────────────┤
                    │ 状态轮转            │ 以状态为中心的系统设计              │
                    │ 事件驱动            │ 基于事件触发的响应式处理            │
                    │ 声明式编程          │ 通过声明定义业务逻辑                │
                    │ 函数绑定            │ 自动函数绑定和元数据管理            │
                    │ 多格式支持          │ 支持DSL、XML、JSON等格式            │
                    │ 错误处理            │ 完善的错误处理和恢复机制            │
                    │ 异步执行            │ 支持异步和并发处理                  │
                    └─────────────────────────────────────────────────────────┘
                    
                    ┌─────────────────────────────────────────────────────────┐
                    │                    架构优势                              │
                    ├─────────────────────────────────────────────────────────┤
                    │ 解耦性              │ 状态与逻辑分离，易于维护            │
                    │ 可扩展性            │ 易于添加新状态和功能                │
                    │ 可观测性            │ 状态流转过程完全可追踪              │
                    │ 可测试性            │ 每个状态可独立测试                  │
                    │ 可配置性            │ 支持多种格式的配置定义              │
                    │ 自愈能力            │ 具备错误恢复和重试机制              │
                    └─────────────────────────────────────────────────────────┘
                """.trimIndent())
            }
        }
    }
    
    /**
     * 创建用户注册状态流
     */
    private fun createUserRegistrationStateFlow(): StateFlow {
        val stateFlow = StateFlow()
        
        // 定义状态
        stateFlow.state("user_unregistered", "用户未注册", StateFlow.StateType.INITIAL, isInitial = true)
        stateFlow.state("user_registering", "用户注册中", StateFlow.StateType.NORMAL)
        stateFlow.state("user_registered", "用户已注册", StateFlow.StateType.FINAL, isFinal = true)
        stateFlow.state("user_registration_failed", "用户注册失败", StateFlow.StateType.ERROR, isError = true)
        
        // 子状态
        stateFlow.state("user_validation", "用户信息验证", StateFlow.StateType.NORMAL, "user_registering")
        stateFlow.state("user_storage", "存储用户信息", StateFlow.StateType.NORMAL, "user_registering")
        stateFlow.state("email_notification", "发送邮件通知", StateFlow.StateType.NORMAL, "user_registered")
        
        // 定义转移
        stateFlow.transition("user_unregistered", "user_registering", StateFlow.TransitionCondition.Success)
        stateFlow.transition("user_registering", "user_registered", StateFlow.TransitionCondition.Success)
        stateFlow.transition("user_registering", "user_registration_failed", StateFlow.TransitionCondition.Failure)
        
        // 子状态转移
        stateFlow.transition("user_validation", "user_storage", StateFlow.TransitionCondition.Success)
        stateFlow.transition("user_validation", "user_registration_failed", StateFlow.TransitionCondition.Failure)
        stateFlow.transition("user_storage", "user_registered", StateFlow.TransitionCondition.Success)
        stateFlow.transition("user_storage", "user_registration_failed", StateFlow.TransitionCondition.Failure)
        stateFlow.transition("user_registered", "email_notification", StateFlow.TransitionCondition.Success)
        
        // 绑定服务函数
        val userService = UserRegistrationService()
        stateFlow.bindFunction("user_validation", userService::validateUser)
        stateFlow.bindFunction("user_storage", userService::storeUser)
        stateFlow.bindFunction("email_notification", userService::sendEmailNotification)
        
        return stateFlow
    }
}