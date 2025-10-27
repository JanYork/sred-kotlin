package me.ixor.sred

import me.ixor.sred.core.*
import me.ixor.sred.declarative.*
import kotlinx.coroutines.*

/**
 * 声明式SRED架构演示
 * 展示如何通过声明式语法定义状态流转和函数绑定
 */
object DeclarativeDemo {
    
    suspend fun runDemo() {
        println("=== 声明式SRED架构演示 ===")
        println("状态轮转与事件驱动结合形架构")
        println()
        
        // 1. 创建状态流定义
        println("1. 创建状态流定义...")
        val stateFlow = StateFlowBuilder.createUserRegistrationFlow()
        println("   ✅ 状态流定义创建完成")
        
        // 2. 创建服务实例并自动绑定函数
        println("\n2. 自动绑定函数到状态...")
        val userService = UserRegistrationService()
        stateFlow.bindFunctions(userService)
        println("   ✅ 函数自动绑定完成")
        
        // 3. 构建状态机
        println("\n3. 构建状态机...")
        val stateMachine = stateFlow.build()
        println("   ✅ 状态机构建完成")
        
        // 4. 创建命令处理器
        println("\n4. 创建命令处理器...")
        val commandHandler = CommandHandler()
        commandHandler.registerStateMachine("user_registration", stateMachine)
        println("   ✅ 命令处理器创建完成")
        
        // 5. 处理用户注册命令
        println("\n5. 处理用户注册命令...")
        val command = UserRegistrationCommand(
            email = "user@example.com",
            password = "password123",
            username = "testuser"
        )
        
        println("   命令参数:")
        println("   - 邮箱: ${command.email}")
        println("   - 密码: ${command.password}")
        println("   - 用户名: ${command.username}")
        
        // 执行命令
        val result = commandHandler.handleCommand(command.toCommand())
        
        if (result.success) {
            println("\n✅ 用户注册命令处理成功!")
            println("   结果数据: ${result.data}")
        } else {
            println("\n❌ 用户注册命令处理失败!")
            println("   错误信息: ${result.error?.message}")
        }
        
        // 6. 显示状态流转过程
        println("\n6. 状态流转过程:")
        showStateFlow()
        
        println("\n=== 演示完成 ===")
        println("声明式SRED架构的优势:")
        println("- 通过声明式语法定义状态流转")
        println("- 自动函数绑定，无需手动注册")
        println("- 支持子状态和复杂的状态关系")
        println("- 命令驱动的状态机执行")
    }
    
    private fun showStateFlow() {
        println("   主状态流转:")
        println("   用户未注册 → 用户正在注册 → 用户注册成功/失败")
        println()
        println("   子状态流转 (用户正在注册):")
        println("   未提交注册 → 参数校验 → 存储用户信息 → 注册完成")
        println()
        println("   子状态流转 (用户注册成功):")
        println("   发送邮件 → 邮件已发送")
        println()
        println("   每个状态都有成功/失败两种转移路径")
        println("   失败时直接跳转到错误状态")
    }
}

/**
 * 状态流构建器扩展
 */
object StateFlowBuilder {
    
    /**
     * 创建用户注册状态流
     */
    fun createUserRegistrationFlow(): StateFlow {
        val flow = StateFlow()
        
        // 主状态定义
        flow.state("user_unregistered", "用户未注册", StateFlow.StateType.INITIAL, isInitial = true)
        flow.state("user_registering", "用户正在注册", StateFlow.StateType.NORMAL)
        flow.state("user_registered", "用户注册成功", StateFlow.StateType.FINAL, isFinal = true)
        flow.state("user_registration_failed", "用户注册失败", StateFlow.StateType.ERROR, isError = true)
        
        // 子状态定义（注册过程中的子状态）
        flow.state("registration_not_submitted", "未提交注册", StateFlow.StateType.INITIAL, "user_registering", isInitial = true)
        flow.state("registration_validating", "参数校验", StateFlow.StateType.NORMAL, "user_registering")
        flow.state("registration_storing", "存储用户信息", StateFlow.StateType.NORMAL, "user_registering")
        flow.state("registration_completed", "注册完成", StateFlow.StateType.FINAL, "user_registering", isFinal = true)
        
        // 邮件发送子状态（注册成功后的子状态）
        flow.state("email_sending", "发送邮件", StateFlow.StateType.NORMAL, "user_registered")
        flow.state("email_sent", "邮件已发送", StateFlow.StateType.FINAL, "user_registered", isFinal = true)
        
        // 主状态转移
        flow.transition("user_unregistered", "user_registering", StateFlow.TransitionCondition.Success)
        flow.transition("user_registering", "user_registered", StateFlow.TransitionCondition.Success)
        flow.transition("user_registering", "user_registration_failed", StateFlow.TransitionCondition.Failure)
        
        // 子状态转移
        flow.transition("registration_not_submitted", "registration_validating", StateFlow.TransitionCondition.Success)
        flow.transition("registration_validating", "registration_storing", StateFlow.TransitionCondition.Success)
        flow.transition("registration_validating", "user_registration_failed", StateFlow.TransitionCondition.Failure)
        flow.transition("registration_storing", "registration_completed", StateFlow.TransitionCondition.Success)
        flow.transition("registration_storing", "user_registration_failed", StateFlow.TransitionCondition.Failure)
        flow.transition("registration_completed", "user_registered", StateFlow.TransitionCondition.Success)
        
        // 邮件发送转移
        flow.transition("user_registered", "email_sending", StateFlow.TransitionCondition.Success)
        flow.transition("email_sending", "email_sent", StateFlow.TransitionCondition.Success)
        
        return flow
    }
}
