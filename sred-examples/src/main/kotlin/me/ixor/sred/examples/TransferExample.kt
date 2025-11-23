package me.ixor.sred.examples

import me.ixor.sred.examples.transfer.runTransfer

/**
 * 转账业务示例入口
 * 
 * 业务代码放在 examples 包中，不在框架 core 中
 */
fun runTransferExample(args: Array<String>) = runTransfer(args)
