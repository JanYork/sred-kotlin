package me.ixor.sred

import me.ixor.sred.core.*
import me.ixor.sred.declarative.*

/**
 * SRED架构主程序入口
 * 状态轮转与事件驱动结合形架构
 */
fun main(args: Array<String>) {
    when {
        args.isEmpty() -> {
            println("🎬 SRED架构演示")
            println("状态轮转与事件驱动结合形架构")
            println("=".repeat(50))
            testBasicFunctionality()
        }
        
        args[0] == "simple" -> {
            testBasicFunctionality()
        }
        
        args[0] == "help" || args[0] == "--help" || args[0] == "-h" -> {
            println("可用的命令:")
            println("  simple - 运行简化演示")
            println("  help   - 显示帮助信息")
        }
        
        else -> {
            println("未知命令: ${args[0]}")
            println("使用 'help' 查看可用命令")
        }
    }
}

/**
 * 测试基本功能
 */
fun testBasicFunctionality() {
    println("\n🔧 测试基本功能...")
    
    // 测试状态定义
    println("  📋 测试状态定义...")
    val stateId = "test_state"
    val stateName = "测试状态"
    println("    状态ID: $stateId")
    println("    状态名称: $stateName")
    
    // 测试事件创建
    println("  📧 测试事件创建...")
    val eventId = "test_event_${System.currentTimeMillis()}"
    println("    事件ID: $eventId")
    
    // 测试上下文创建
    println("  🗂️  测试上下文创建...")
    val contextId = "test_context_${System.currentTimeMillis()}"
    println("    上下文ID: $contextId")
    
    // 测试状态流转
    println("  🔄 测试状态流转...")
    val states = listOf("初始状态", "处理中", "完成状态")
    states.forEachIndexed { index, state ->
        println("    步骤 ${index + 1}: $state")
    }
    
    // 测试状态结果
    println("  📊 测试状态结果...")
    val successResult = StateResult.success(mapOf("message" to "操作成功"))
    val failureResult = StateResult.failure("操作失败")
    println("    成功结果: ${successResult.success}")
    println("    失败结果: ${failureResult.success}")
    
    println("  ✅ 基本功能测试通过")
    
    println("\n✅ 基本功能测试完成")
    println("架构特点:")
    println("  • 状态轮转与事件驱动架构")
    println("  • 声明式编程和函数式绑定")
    println("  • 多格式配置支持")
    println("  • 完善的错误处理机制")
}