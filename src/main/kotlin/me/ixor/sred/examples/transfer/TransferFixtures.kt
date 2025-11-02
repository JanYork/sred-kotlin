package me.ixor.sred.examples.transfer

/**
 * 转账测试数据构建器
 */
object TransferFixtures {
    fun createDefault(): TransferContext {
        val users = mapOf(
            "userA" to User("userA", "张三", "accountA"),
            "userB" to User("userB", "李四", "accountB")
        )
        val accounts = mutableMapOf(
            "accountA" to Account("accountA", "userA", 1000.0),
            "accountB" to Account("accountB", "userB", 500.0)
        )
        return TransferContext(users, accounts)
    }
}

/**
 * 转账上下文
 */
data class TransferContext(
    val users: Map<String, User>,
    val accounts: MutableMap<String, Account>
)

