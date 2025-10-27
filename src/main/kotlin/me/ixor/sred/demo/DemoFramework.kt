package me.ixor.sred.demo

import kotlinx.coroutines.delay
import kotlin.system.measureTimeMillis

/**
 * 演示框架
 * 提供统一的演示执行、计时和结果展示功能
 */
object DemoFramework {
    
    /**
     * 演示结果
     */
    data class DemoResult(
        val name: String,
        val success: Boolean,
        val duration: Long,
        val message: String? = null,
        val data: Map<String, Any> = emptyMap()
    )
    
    /**
     * 演示配置
     */
    data class DemoConfig(
        val name: String,
        val description: String,
        val category: String = "General",
        val timeout: Long = 30000L,
        val retryCount: Int = 0,
        val showDetails: Boolean = true
    )
    
    /**
     * 执行演示
     */
    suspend fun runDemo(
        config: DemoConfig,
        block: suspend () -> Unit
    ): DemoResult {
        val startTime = System.currentTimeMillis()
        var success = false
        var message: String? = null
        var data = mutableMapOf<String, Any>()
        
        try {
            println("🚀 开始演示: ${config.name}")
            println("📝 描述: ${config.description}")
            println("📂 分类: ${config.category}")
            println("⏱️  超时: ${config.timeout}ms")
            println("🔄 重试次数: ${config.retryCount}")
            println("=" * 60)
            
            val duration = measureTimeMillis {
                block()
            }
            
            success = true
            message = "演示执行成功"
            data["duration"] = duration
            data["category"] = config.category
            
            println("✅ 演示完成: ${config.name}")
            println("⏱️  耗时: ${duration}ms")
            
        } catch (e: Exception) {
            success = false
            message = "演示执行失败: ${e.message}"
            data["error"] = e.message ?: "未知错误"
            data["errorType"] = e.javaClass.simpleName
            
            println("❌ 演示失败: ${config.name}")
            println("💥 错误信息: ${e.message}")
            if (config.showDetails) {
                e.printStackTrace()
            }
        }
        
        val totalDuration = System.currentTimeMillis() - startTime
        println("=" * 60)
        
        return DemoResult(
            name = config.name,
            success = success,
            duration = totalDuration,
            message = message,
            data = data
        )
    }
    
    /**
     * 批量执行演示
     */
    suspend fun runDemos(
        demos: List<Pair<DemoConfig, suspend () -> Unit>>
    ): List<DemoResult> {
        val results = mutableListOf<DemoResult>()
        
        println("🎬 开始批量演示")
        println("📊 演示数量: ${demos.size}")
        println("=" * 80)
        
        demos.forEachIndexed { index, (config, block) ->
            println("\n📋 演示 ${index + 1}/${demos.size}")
            val result = runDemo(config, block)
            results.add(result)
            
            if (index < demos.size - 1) {
                println("\n" + "-" * 40)
                delay(1000) // 演示间暂停
            }
        }
        
        // 显示汇总结果
        showSummary(results)
        
        return results
    }
    
    /**
     * 显示汇总结果
     */
    private fun showSummary(results: List<DemoResult>) {
        println("\n" + "=" * 80)
        println("📊 演示汇总结果")
        println("=" * 80)
        
        val successCount = results.count { it.success }
        val failureCount = results.size - successCount
        val totalDuration = results.sumOf { it.duration }
        val avgDuration = if (results.isNotEmpty()) totalDuration / results.size else 0
        
        println("✅ 成功: $successCount")
        println("❌ 失败: $failureCount")
        println("⏱️  总耗时: ${totalDuration}ms")
        println("📈 平均耗时: ${avgDuration}ms")
        
        if (failureCount > 0) {
            println("\n❌ 失败的演示:")
            results.filter { !it.success }.forEach { result ->
                println("  • ${result.name}: ${result.message}")
            }
        }
        
        println("\n📋 详细结果:")
        results.forEach { result ->
            val status = if (result.success) "✅" else "❌"
            println("  $status ${result.name} (${result.duration}ms)")
        }
        
        println("=" * 80)
    }
    
    /**
     * 创建演示配置
     */
    fun createConfig(
        name: String,
        description: String,
        category: String = "General",
        timeout: Long = 30000L,
        retryCount: Int = 0,
        showDetails: Boolean = true
    ): DemoConfig {
        return DemoConfig(
            name = name,
            description = description,
            category = category,
            timeout = timeout,
            retryCount = retryCount,
            showDetails = showDetails
        )
    }
    
    /**
     * 演示步骤
     */
    suspend fun step(stepName: String, block: suspend () -> Unit) {
        println("  🔄 $stepName...")
        val duration = measureTimeMillis {
            block()
        }
        println("  ✅ $stepName 完成 (${duration}ms)")
    }
    
    /**
     * 演示信息
     */
    fun info(message: String) {
        println("  ℹ️  $message")
    }
    
    /**
     * 演示警告
     */
    fun warning(message: String) {
        println("  ⚠️  $message")
    }
    
    /**
     * 演示错误
     */
    fun error(message: String) {
        println("  ❌ $message")
    }
    
    /**
     * 演示成功
     */
    fun success(message: String) {
        println("  ✅ $message")
    }
}

/**
 * 字符串重复扩展函数
 */
private operator fun String.times(n: Int): String = this.repeat(n)
