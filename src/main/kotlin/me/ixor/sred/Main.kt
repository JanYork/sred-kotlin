package me.ixor.sred

import me.ixor.sred.core.*
import me.ixor.sred.declarative.*
import me.ixor.sred.event.*
import me.ixor.sred.state.*
import me.ixor.sred.orchestrator.*
import me.ixor.sred.declarative.format.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * SRED架构主程序入口
 * 用户转账案例：用户A -> 用户B 转账
 */
fun main(args: Array<String>) = runBlocking {
    println("=".repeat(60))
    println("SRED架构 - 用户转账案例")
    println("=".repeat(60))
    
    // ========== 1. 初始化数据表（模拟数据库） ==========
    // 用户表
    data class User(val userId: String, val name: String, val accountId: String)
    val usersTable = ConcurrentHashMap<String, User>()
    usersTable["userA"] = User("userA", "张三", "accountA")
    usersTable["userB"] = User("userB", "李四", "accountB")
    
    // 账户表
    data class Account(val accountId: String, val userId: String, var balance: Double)
    val accountsTable = ConcurrentHashMap<String, Account>()
    accountsTable["accountA"] = Account("accountA", "userA", 1000.0)
    accountsTable["accountB"] = Account("accountB", "userB", 500.0)
    
    println("\n📊 初始账户余额：")
    println("   ${usersTable["userA"]?.name} (账户A): ${accountsTable["accountA"]?.balance}元")
    println("   ${usersTable["userB"]?.name} (账户B): ${accountsTable["accountB"]?.balance}元")
    
    // ========== 2. 使用JSON格式定义状态流程 ==========
    val jsonConfig = """
    {
      "name": "转账流程",
      "description": "用户A向用户B转账的状态流程",
      "version": "1.0.0",
      "states": [
        {
          "id": "transfer_initiated",
          "name": "转账已发起",
          "type": "INITIAL",
          "isInitial": true
        },
        {
          "id": "validating_accounts",
          "name": "验证账户",
          "type": "NORMAL"
        },
        {
          "id": "checking_balance",
          "name": "检查余额",
          "type": "NORMAL"
        },
        {
          "id": "transferring",
          "name": "执行转账",
          "type": "NORMAL"
        },
        {
          "id": "transfer_success",
          "name": "转账成功",
          "type": "FINAL",
          "isFinal": true
        },
        {
          "id": "transfer_failed",
          "name": "转账失败",
          "type": "ERROR",
          "isError": true
        }
      ],
      "transitions": [
        {
          "from": "transfer_initiated",
          "to": "validating_accounts",
          "condition": "Success",
          "priority": 1
        },
        {
          "from": "validating_accounts",
          "to": "checking_balance",
          "condition": "Success",
          "priority": 1
        },
        {
          "from": "validating_accounts",
          "to": "transfer_failed",
          "condition": "Failure",
          "priority": 0
        },
        {
          "from": "checking_balance",
          "to": "transferring",
          "condition": "Success",
          "priority": 1
        },
        {
          "from": "checking_balance",
          "to": "transfer_failed",
          "condition": "Failure",
          "priority": 0
        },
        {
          "from": "transferring",
          "to": "transfer_success",
          "condition": "Success",
          "priority": 1
        },
        {
          "from": "transferring",
          "to": "transfer_failed",
          "condition": "Failure",
          "priority": 0
        }
      ]
    }
    """.trimIndent()
    
    // ========== 3. 解析JSON配置并构建状态流 ==========
    val stateFlow = FormatLoader.loadFromString(jsonConfig, FormatLoader.FormatType.JSON)
    
    // ========== 4. 绑定状态处理函数 ==========
    stateFlow.bindFunctions {
        // 验证账户状态
        bind("validating_accounts") { context ->
            println("\n🔍 验证账户...")
            delay(100) // 模拟验证时间
            
            val fromUserId = context.localState["fromUserId"] as? String
            val toUserId = context.localState["toUserId"] as? String
            
            val fromUser = usersTable[fromUserId]
            val toUser = usersTable[toUserId]
            
            if (fromUser == null || toUser == null) {
                println("   ❌ 账户验证失败：用户不存在")
                StateResult.failure("账户不存在")
            } else {
                println("   ✅ 账户验证成功")
                println("       转出账户: ${fromUser.name} (${fromUser.accountId})")
                println("       转入账户: ${toUser.name} (${toUser.accountId})")
                StateResult.success(mapOf(
                    "fromAccountId" to fromUser.accountId,
                    "toAccountId" to toUser.accountId
                ))
            }
        }
        
        // 检查余额状态
        bind("checking_balance") { context ->
            println("\n💰 检查余额...")
            delay(100)
            
            val amount = (context.localState["amount"] as? Number)?.toDouble() ?: 0.0
            val fromAccountId = context.localState["fromAccountId"] as? String
            
            val fromAccount = accountsTable[fromAccountId]
            
            if (fromAccount == null) {
                println("   ❌ 账户不存在")
                StateResult.failure("账户不存在")
            } else if (fromAccount.balance < amount) {
                println("   ❌ 余额不足")
                println("       当前余额: ${fromAccount.balance}元")
                println("       转账金额: ${amount}元")
                StateResult.failure("余额不足")
            } else {
                println("   ✅ 余额检查通过")
                println("       当前余额: ${fromAccount.balance}元")
                println("       转账金额: ${amount}元")
                println("       转账后余额: ${fromAccount.balance - amount}元")
                StateResult.success()
            }
        }
        
        // 执行转账状态
        bind("transferring") { context ->
            println("\n💸 执行转账...")
            delay(200) // 模拟转账处理时间
            
            val amount = (context.localState["amount"] as? Number)?.toDouble() ?: 0.0
            val fromAccountId = context.localState["fromAccountId"] as? String
            val toAccountId = context.localState["toAccountId"] as? String
            
            val fromAccount = accountsTable[fromAccountId]
            val toAccount = accountsTable[toAccountId]
            
            if (fromAccount == null || toAccount == null) {
                println("   ❌ 转账失败：账户不存在")
                StateResult.failure("账户不存在")
            } else {
                // 执行转账（扣款和入账）
                fromAccount.balance -= amount
                toAccount.balance += amount
                
                println("   ✅ 转账成功！")
                println("       转出账户余额: ${fromAccount.balance}元")
                println("       转入账户余额: ${toAccount.balance}元")
                
                StateResult.success(mapOf(
                    "transferId" to "TXN_${System.currentTimeMillis()}",
                    "transferAmount" to amount,
                    "fromBalance" to fromAccount.balance,
                    "toBalance" to toAccount.balance
                ))
            }
        }
        
        // 转账失败状态
        bind("transfer_failed") { context ->
            println("\n❌ 转账失败")
            val error = context.localState["error"] as? String
            if (error != null) {
                println("   错误信息: $error")
            }
            StateResult.failure(error ?: "未知错误")
        }
        
        // 转账成功状态
        bind("transfer_success") { context ->
            println("\n🎉 转账流程完成！")
            val transferId = context.localState["transferId"] as? String
            val amount = context.localState["transferAmount"] as? Number
            println("   交易ID: $transferId")
            println("   转账金额: ${amount}元")
            StateResult.success()
        }
    }
    
    // ========== 5. 构建状态机 ==========
    val stateMachine = stateFlow.build()
    
    // ========== 6. 创建转账实例 ==========
    val transferId = "transfer_${System.currentTimeMillis()}"
    val initialContext = StateContextFactory.create(
        localState = mapOf(
            "transferId" to transferId,
            "fromUserId" to "userA",
            "toUserId" to "userB",
            "amount" to 200.0
        )
    )
    
    println("\n🚀 启动转账流程...")
    println("   转账ID: $transferId")
    println("   转出用户: ${usersTable["userA"]?.name}")
    println("   转入用户: ${usersTable["userB"]?.name}")
    println("   转账金额: 200.0元")
    
    val instance = stateMachine.start(transferId, initialContext)
    
    // ========== 7. 逐步发送事件触发状态转移 ==========
    var currentState = instance.getCurrentState()
    var step = 1
    val stateHistory = mutableListOf<String>()
    stateHistory.add(currentState ?: "unknown")
    
    println("\n🔄 开始状态流转...")
    
    // 自动执行状态流转直到完成
    while (currentState != null && currentState != "transfer_success" && currentState != "transfer_failed") {
        println("\n--- 步骤 $step: 当前状态 - $currentState ---")
        
        val processEvent = EventFactory.create(
            type = EventType("transfer", "process"),
            name = "处理转账",
            payload = mapOf("step" to step)
        )
        
        val result = instance.processEvent(processEvent)
        val previousState = currentState
        currentState = instance.getCurrentState()
        
        if (currentState != previousState && currentState != null) {
            stateHistory.add(currentState)
        }
        
        if (!result.success) {
            println("❌ 处理失败: ${result.error?.message}")
            break
        }
        
        if (currentState == "transfer_success" || currentState == "transfer_failed") {
            break
        }
        
        step++
        delay(300) // 短暂延迟以便观察
    }
    
    // ========== 8. 显示最终结果 ==========
    println("\n" + "=".repeat(60))
    println("📊 最终账户余额：")
    println("   ${usersTable["userA"]?.name} (账户A): ${accountsTable["accountA"]?.balance}元")
    println("   ${usersTable["userB"]?.name} (账户B): ${accountsTable["accountB"]?.balance}元")
    
    // ========== 9. 显示状态轨迹 ==========
    println("\n📈 状态流转轨迹：")
    stateHistory.forEachIndexed { index, state ->
        if (index == 0) {
            println("   ${index + 1}. [初始] -> $state")
        } else {
            val fromState = stateHistory[index - 1]
            val suffix = if (index == stateHistory.size - 1) " (最终)" else ""
            println("   ${index + 1}. $fromState -> $state$suffix")
        }
    }
    
    println("\n" + "=".repeat(60))
    println("✅ 转账流程执行完成！")
}
