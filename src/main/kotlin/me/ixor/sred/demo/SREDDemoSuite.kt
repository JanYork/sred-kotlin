package me.ixor.sred.demo

import kotlinx.coroutines.runBlocking

/**
 * SRED演示套件
 * 统一的演示入口，展示状态轮转与事件驱动架构的各种特性
 */
object SREDDemoSuite {
    
    /**
     * 运行完整演示套件
     */
    fun runFullDemo() = runBlocking {
        println("🎬 SRED架构演示套件")
        println("状态轮转与事件驱动结合形架构")
        println("=" * 80)
        
        val demos = listOf(
            DemoFramework.createConfig(
                name = "SRED架构最终标准演示",
                description = "展示状态轮转与事件驱动架构的核心功能和特性",
                category = "核心功能"
            ) to { runBlocking { FinalStandardDemo.runDemo() } },
            
            DemoFramework.createConfig(
                name = "多格式配置演示",
                description = "展示如何使用DSL、XML、JSON等格式定义状态流转",
                category = "配置管理"
            ) to { runBlocking { FormatConfigDemo.runDemo() } }
        )
        
        val results = DemoFramework.runDemos(demos)
        
        // 显示最终总结
        showFinalSummary(results)
    }
    
    /**
     * 运行特定演示
     */
    fun runSpecificDemo(demoName: String) = runBlocking {
        when (demoName.lowercase()) {
            "standard", "标准" -> {
                val result = runBlocking { FinalStandardDemo.runDemo() }
                showDemoResult(result)
            }
            "format", "格式" -> {
                val result = runBlocking { FormatConfigDemo.runDemo() }
                showDemoResult(result)
            }
            else -> {
                println("❌ 未知的演示名称: $demoName")
                println("可用的演示: standard, format")
            }
        }
    }
    
    /**
     * 显示演示结果
     */
    private fun showDemoResult(result: DemoFramework.DemoResult) {
        println("\n" + "=" * 60)
        println("📊 演示结果")
        println("=" * 60)
        println("名称: ${result.name}")
        println("状态: ${if (result.success) "✅ 成功" else "❌ 失败"}")
        println("耗时: ${result.duration}ms")
        if (result.message != null) {
            println("信息: ${result.message}")
        }
        if (result.data.isNotEmpty()) {
            println("数据: ${result.data}")
        }
        println("=" * 60)
    }
    
    /**
     * 显示最终总结
     */
    private fun showFinalSummary(results: List<DemoFramework.DemoResult>) {
        println("\n" + "🎉" * 20)
        println("🎉 SRED架构演示套件执行完成")
        println("🎉" * 20)
        
        val successCount = results.count { it.success }
        val failureCount = results.size - successCount
        val totalDuration = results.sumOf { it.duration }
        val avgDuration = if (results.isNotEmpty()) totalDuration / results.size else 0
        
        println("\n📊 执行统计:")
        println("  ✅ 成功演示: $successCount")
        println("  ❌ 失败演示: $failureCount")
        println("  ⏱️  总耗时: ${totalDuration}ms")
        println("  📈 平均耗时: ${avgDuration}ms")
        
        println("\n🏆 演示亮点:")
        println("  • 状态轮转与事件驱动架构的完整实现")
        println("  • 支持多种格式配置（DSL、XML、JSON）")
        println("  • 丰富的注解系统和元数据支持")
        println("  • 完善的错误处理和重试机制")
        println("  • 异步执行和并发处理能力")
        println("  • 声明式编程和函数式绑定")
        
        println("\n🚀 架构优势:")
        println("  • 从命令控制到状态秩序的范式演进")
        println("  • 系统自组织和自治能力")
        println("  • 高可观测性和可追踪性")
        println("  • 天然的解耦性和高扩展性")
        println("  • 语义级自愈能力")
        
        println("\n📚 技术特性:")
        println("  • 状态机驱动的业务流程")
        println("  • 事件驱动的异步处理")
        println("  • 注解绑定的声明式编程")
        println("  • 多格式配置支持")
        println("  • 完善的错误处理机制")
        println("  • 灵活的元数据系统")
        
        println("\n" + "=" * 80)
        println("感谢使用SRED架构演示套件！")
        println("状态轮转让软件有了灵魂，事件驱动让软件有了呼吸。")
        println("=" * 80)
    }
    
    /**
     * 显示帮助信息
     */
    fun showHelp() {
        println("""
            🎬 SRED架构演示套件
            
            用法:
              java -jar sred-demo.jar [选项] [演示名称]
            
            选项:
              --help, -h     显示帮助信息
              --list, -l     列出所有可用演示
              --full, -f     运行完整演示套件（默认）
            
            演示名称:
              standard       SRED架构标准演示
              format         多格式配置演示
            
            示例:
              java -jar sred-demo.jar                    # 运行完整演示
              java -jar sred-demo.jar standard          # 运行标准演示
              java -jar sred-demo.jar --list             # 列出所有演示
              java -jar sred-demo.jar --help             # 显示帮助
        """.trimIndent())
    }
    
    /**
     * 列出所有可用演示
     */
    fun listDemos() {
        println("📋 可用的演示:")
        println("  standard  - SRED架构标准演示")
        println("  format    - 多格式配置演示")
        println("  full      - 完整演示套件")
    }
}

/**
 * 字符串重复扩展函数
 */
private operator fun String.times(n: Int): String = this.repeat(n)
