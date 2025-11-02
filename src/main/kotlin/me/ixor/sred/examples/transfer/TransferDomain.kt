package me.ixor.sred.examples.transfer

/**
 * 转账领域模型
 */
data class User(val userId: String, val name: String, val accountId: String)
data class Account(val accountId: String, val userId: String, var balance: Double)

