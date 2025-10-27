package me.ixor.sred

import me.ixor.sred.demo.SREDDemoSuite

/**
 * SRED架构主程序入口
 * 
 * 演示状态轮转与事件驱动结合形架构的实际应用
 */
fun main(args: Array<String>) {
    try {
        when {
            args.isEmpty() || args[0] == "--full" || args[0] == "-f" -> {
                SREDDemoSuite.runFullDemo()
            }
            args[0] == "--help" || args[0] == "-h" -> {
                SREDDemoSuite.showHelp()
            }
            args[0] == "--list" || args[0] == "-l" -> {
                SREDDemoSuite.listDemos()
            }
            else -> {
                SREDDemoSuite.runSpecificDemo(args[0])
            }
        }
    } catch (e: Exception) {
        println("❌ 程序执行出错: ${e.message}")
        e.printStackTrace()
        println("\n使用 --help 查看帮助信息")
    }
}