package me.ixor.sred.transfer

import kotlinx.coroutines.runBlocking
import me.ixor.sred.core.StateContextFactory
import me.ixor.sred.declarative.StateResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 针对 TransferService 的最小行为回归测试，
 * 覆盖账户验证、余额检查和执行转账的核心路径。
 */
class TransferServiceTest {

    @Test
    fun `validateAccounts should populate account ids when users exist`() = runBlocking {
        val userRepository = InMemoryTransferUserRepository().apply {
            put(User("userA", "张三", "accountA"))
            put(User("userB", "李四", "accountB"))
        }
        val accountRepository = InMemoryAccountRepository().apply {
            put(Account("accountA", "userA", 1000.0))
            put(Account("accountB", "userB", 500.0))
        }
        val service = TransferService(userRepository, accountRepository)

        val context = StateContextFactory.create(
            localState = mapOf(
                "fromUserId" to "userA",
                "toUserId" to "userB",
                "amount" to 100.0
            )
        )

        val result = service.validateAccounts(context)

        assertTrue(result is StateResult.Success)
        val data = (result as StateResult.Success).data
        assertEquals("accountA", data["fromAccountId"])
        assertEquals("accountB", data["toAccountId"])
    }

    @Test
    fun `executeTransfer should move balance between accounts`() = runBlocking {
        val userRepository = InMemoryTransferUserRepository().apply {
            put(User("userA", "张三", "accountA"))
            put(User("userB", "李四", "accountB"))
        }
        val accountRepository = InMemoryAccountRepository().apply {
            put(Account("accountA", "userA", 1000.0))
            put(Account("accountB", "userB", 500.0))
        }
        val service = TransferService(userRepository, accountRepository)

        val context = StateContextFactory.create(
            localState = mapOf(
                "fromAccountId" to "accountA",
                "toAccountId" to "accountB",
                "amount" to 200.0
            )
        )

        val result = service.executeTransfer(context)

        assertTrue(result is StateResult.Success)
        val accounts = accountRepository.allAccounts()
        assertEquals(800.0, accounts["accountA"]?.balance)
        assertEquals(700.0, accounts["accountB"]?.balance)
    }
}
