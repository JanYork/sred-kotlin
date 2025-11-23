package me.ixor.sred.core

/**
 * SRED 架构基础异常类
 */
open class SredException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 状态相关异常
 */
class StateException(
    message: String,
    cause: Throwable? = null
) : SredException(message, cause)

/**
 * 事件相关异常
 */
class EventException(
    message: String,
    cause: Throwable? = null
) : SredException(message, cause)

/**
 * 持久化相关异常
 */
class PersistenceException(
    message: String,
    cause: Throwable? = null
) : SredException(message, cause)

/**
 * 配置相关异常
 */
class ConfigurationException(
    message: String,
    cause: Throwable? = null
) : SredException(message, cause)

/**
 * 安全相关异常
 */
class SecurityException(
    message: String,
    cause: Throwable? = null
) : SredException(message, cause)

/**
 * 资源相关异常
 */
class ResourceException(
    message: String,
    cause: Throwable? = null
) : SredException(message, cause)

