package me.ixor.sred.core

import mu.KotlinLogging
import mu.KLogger

/**
 * 日志工具类
 * 使用 kotlin-logging 提供更好的 Kotlin DSL 和彩色输出支持
 */

/**
 * 获取日志记录器（kotlin-logging）
 * kotlin-logging 的 Logger 对象使用 lambda 表达式调用日志方法
 * 例如：log.info { "message" }
 */
inline fun <reified T> logger(): KLogger {
    return KotlinLogging.logger(T::class.java.name)
}

/**
 * 为任何类提供日志功能（扩展函数）
 */
inline fun <reified T> T.logger(): KLogger {
    return KotlinLogging.logger(T::class.java.name)
}

/**
 * 获取指定名称的日志记录器
 */
fun logger(name: String): KLogger {
    return KotlinLogging.logger(name)
}

