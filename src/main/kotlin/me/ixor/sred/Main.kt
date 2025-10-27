package me.ixor.sred

import kotlinx.coroutines.runBlocking

/**
 * SRED架构主程序入口
 * 
 * 演示状态轮转与事件驱动结合形架构的实际应用
 */
fun main() = runBlocking {
    try {
        // 运行声明式演示
        DeclarativeDemo.runDemo()
        
    } catch (e: Exception) {
        println("程序执行出错: ${e.message}")
        e.printStackTrace()
    }
}
