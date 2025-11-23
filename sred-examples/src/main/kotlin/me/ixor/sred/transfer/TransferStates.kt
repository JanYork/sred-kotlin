package me.ixor.sred.transfer

/**
 * 转账状态ID常量
 *
 * 业务层定义，不在框架 core 中；使用 object 定义编译时常量，支持注解使用。
 */
object TransferStates {
    const val INITIATED = "transfer_initiated"
    const val VALIDATING_ACCOUNTS = "validating_accounts"
    const val CHECKING_BALANCE = "checking_balance"
    const val TRANSFERRING = "transferring"
    const val SUCCESS = "transfer_success"
    const val FAILED = "transfer_failed"
}

