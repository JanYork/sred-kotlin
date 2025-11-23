package me.ixor.sred.examples.transfer

import me.ixor.sred.core.logger
import me.ixor.sred.transfer.Account

/**
 * 转账视图 - 负责显示和格式化输出
 */
object TransferView {
    private val log = logger<TransferView>()
    
    fun showInitialBalance(accounts: MutableMap<String, Account>) {
        log.info { "初始余额: 张三=${accounts["accountA"]?.balance}元, 李四=${accounts["accountB"]?.balance}元" }
    }
    
    fun showFinalBalance(accounts: MutableMap<String, Account>) {
        log.info { "\n最终余额: 张三=${accounts["accountA"]?.balance}元, 李四=${accounts["accountB"]?.balance}元" }
    }
    
    fun showTransferId(transferId: String?) {
        transferId?.let { log.info { "交易ID: $it" } }
    }
}
