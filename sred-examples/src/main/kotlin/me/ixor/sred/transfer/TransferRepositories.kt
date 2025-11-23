package me.ixor.sred.transfer

import java.util.concurrent.ConcurrentHashMap

/**
 * 转账领域仓储接口定义。
 * 当前提供内存实现，后续可以替换为数据库等持久化实现。
 */
interface TransferUserRepository {
    fun findById(userId: String): User?
}

interface AccountRepository {
    fun findById(accountId: String): Account?
}

class InMemoryTransferUserRepository(
    private val users: MutableMap<String, User> = ConcurrentHashMap()
) : TransferUserRepository {

    override fun findById(userId: String): User? = users[userId]

    /**
     * 仅用于示例或测试初始化数据。
     */
    fun put(user: User) {
        users[user.userId] = user
    }

    fun allUsers(): Map<String, User> = users.toMap()
}

class InMemoryAccountRepository(
    private val accounts: MutableMap<String, Account> = ConcurrentHashMap()
) : AccountRepository {

    override fun findById(accountId: String): Account? = accounts[accountId]

    /**
     * 仅用于示例或测试初始化数据。
     */
    fun put(account: Account) {
        accounts[account.accountId] = account
    }

    fun allAccounts(): MutableMap<String, Account> = accounts
}
