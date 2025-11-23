package me.ixor.sred.transfer

import kotlinx.coroutines.delay
import me.ixor.sred.core.StateContext
import me.ixor.sred.core.getLocalState
import me.ixor.sred.core.logger
import me.ixor.sred.declarative.StateResult
import me.ixor.sred.declarative.annotations.StateHandler
import org.springframework.stereotype.Service

/**
 * 转账服务 - 状态处理器
 *
 * 仅依赖 StateContext 抽象，与具体 UI 或示例入口解耦。
 */
@Service
class TransferService(
    private val userRepository: TransferUserRepository,
    private val accountRepository: AccountRepository
) {
    private val log = logger<TransferService>()

    @StateHandler(TransferStates.VALIDATING_ACCOUNTS, "验证账户")
    suspend fun validateAccounts(context: StateContext): StateResult {
        log.info { "  ✓ 验证账户" }
        delay(100)

        val from = context.getLocalState<String>("fromUserId")?.let { userRepository.findById(it) }
        val to = context.getLocalState<String>("toUserId")?.let { userRepository.findById(it) }

        return if (from == null || to == null) {
            StateResult.failure("账户不存在")
        } else {
            StateResult.success(
                mapOf(
                    "fromAccountId" to from.accountId,
                    "toAccountId" to to.accountId
                )
            )
        }
    }

    @StateHandler(TransferStates.CHECKING_BALANCE, "检查余额")
    suspend fun checkBalance(context: StateContext): StateResult {
        log.info { "  ✓ 检查余额" }
        delay(100)

        val amount = context.getLocalState<Number>("amount")?.toDouble() ?: 0.0
        val account = context.getLocalState<String>("fromAccountId")?.let { accountRepository.findById(it) }

        return when {
            account == null -> StateResult.failure("账户不存在")
            account.balance < amount -> StateResult.failure("余额不足")
            else -> StateResult.success()
        }
    }

    @StateHandler(TransferStates.TRANSFERRING, "执行转账")
    suspend fun executeTransfer(context: StateContext): StateResult {
        log.info { "  ✓ 执行转账" }
        delay(200)

        val amount = context.getLocalState<Number>("amount")?.toDouble() ?: 0.0
        val from = context.getLocalState<String>("fromAccountId")?.let { accountRepository.findById(it) }
        val to = context.getLocalState<String>("toAccountId")?.let { accountRepository.findById(it) }

        return if (from == null || to == null) {
            StateResult.failure("账户不存在")
        } else {
            from.balance -= amount
            to.balance += amount
            StateResult.success(
                mapOf(
                    "transferId" to "TXN_${System.currentTimeMillis()}",
                    "transferAmount" to amount
                )
            )
        }
    }

    @StateHandler(TransferStates.FAILED, "处理失败")
    suspend fun handleFailed(context: StateContext): StateResult {
        log.error { "  ✗ 转账失败" }
        return StateResult.failure(context.getLocalState<String>("error") ?: "未知错误")
    }

    @StateHandler(TransferStates.SUCCESS, "处理成功")
    suspend fun handleSuccess(context: StateContext): StateResult {
        log.info { "  ✓ 转账成功" }
        return StateResult.success()
    }
}
