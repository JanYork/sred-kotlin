package me.ixor.sred.examples.transfer

import me.ixor.sred.core.*
import me.ixor.sred.declarative.StateResult
import me.ixor.sred.declarative.annotations.StateHandler
import me.ixor.sred.core.logger
import kotlinx.coroutines.*

/**
 * 转账服务 - 状态处理器
 */
class TransferService(
    private val users: Map<String, User>,
    private val accounts: MutableMap<String, Account>
) {
    private val log = logger<TransferService>()
    
    @StateHandler(TransferStates.VALIDATING_ACCOUNTS, "验证账户")
    suspend fun validateAccounts(context: StateContext): StateResult {
        log.info { "  ✓ 验证账户" }
        delay(100)
        
        val from = users[context.getLocalState<String>("fromUserId")]
        val to = users[context.getLocalState<String>("toUserId")]
        
        return if (from == null || to == null) {
            StateResult.failure("账户不存在")
        } else {
            StateResult.success(mapOf(
                "fromAccountId" to from.accountId,
                "toAccountId" to to.accountId
            ))
        }
    }
    
    @StateHandler(TransferStates.CHECKING_BALANCE, "检查余额")
    suspend fun checkBalance(context: StateContext): StateResult {
        log.info { "  ✓ 检查余额" }
        delay(100)
        
        val amount = context.getLocalState<Number>("amount")?.toDouble() ?: 0.0
        val account = accounts[context.getLocalState<String>("fromAccountId")]
        
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
        val from = accounts[context.getLocalState<String>("fromAccountId")]
        val to = accounts[context.getLocalState<String>("toAccountId")]
        
        return if (from == null || to == null) {
            StateResult.failure("账户不存在")
        } else {
            from.balance -= amount
            to.balance += amount
            StateResult.success(mapOf(
                "transferId" to "TXN_${System.currentTimeMillis()}",
                "transferAmount" to amount
            ))
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

