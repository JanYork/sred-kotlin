package me.ixor.sred.transfer

/**
 * 转账领域模型
 *
 * 与示例入口（examples.transfer）分离，供 API 与业务层复用。
 */
data class User(val userId: String, val name: String, val accountId: String)

data class Account(val accountId: String, val userId: String, var balance: Double)

