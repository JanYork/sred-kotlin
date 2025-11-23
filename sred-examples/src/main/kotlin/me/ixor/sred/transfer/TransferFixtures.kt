package me.ixor.sred.transfer

/**
 * 转账测试数据构建器
 *
 * 在示例或本地开发场景下，提供基于内存仓储的上下文对象。
 * 生产环境中由 Spring 管理实际的仓储与服务实例。
 */
object TransferFixtures {
    fun createDefault(): TransferContext {
        val userRepository = InMemoryTransferUserRepository()
        val accountRepository = InMemoryAccountRepository()

        userRepository.put(User("userA", "张三", "accountA"))
        userRepository.put(User("userB", "李四", "accountB"))

        accountRepository.put(Account("accountA", "userA", 1000.0))
        accountRepository.put(Account("accountB", "userB", 500.0))

        return TransferContext(userRepository, accountRepository)
    }
}

/**
 * 转账上下文（示例用）
 */
data class TransferContext(
    val userRepository: TransferUserRepository,
    val accountRepository: InMemoryAccountRepository
)

