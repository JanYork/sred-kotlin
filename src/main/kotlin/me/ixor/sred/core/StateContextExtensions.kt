package me.ixor.sred.core

/**
 * StateContext 扩展函数
 * 提供类型安全的状态值访问，避免使用 as 类型转换
 */

/**
 * 类型安全地获取局部状态值
 */
inline fun <reified T> StateContext.getLocalState(key: String): T? {
    return getLocalState(key, T::class.java)
}

/**
 * 类型安全地获取全局状态值
 */
inline fun <reified T> StateContext.getGlobalState(key: String): T? {
    return getGlobalState(key, T::class.java)
}

/**
 * 类型安全地获取元信息值
 */
inline fun <reified T> StateContext.getMetadata(key: String): T? {
    return getMetadata(key, T::class.java)
}

/**
 * 类型安全地获取局部状态值，如果不存在或类型不匹配则返回默认值
 */
inline fun <reified T> StateContext.getLocalStateOrDefault(key: String, defaultValue: T): T {
    return getLocalState<T>(key) ?: defaultValue
}

/**
 * 类型安全地获取全局状态值，如果不存在或类型不匹配则返回默认值
 */
inline fun <reified T> StateContext.getGlobalStateOrDefault(key: String, defaultValue: T): T {
    return getGlobalState<T>(key) ?: defaultValue
}

/**
 * 类型安全地获取元信息值，如果不存在或类型不匹配则返回默认值
 */
inline fun <reified T> StateContext.getMetadataOrDefault(key: String, defaultValue: T): T {
    return getMetadata<T>(key) ?: defaultValue
}

