package me.ixor.sred

import me.ixor.sred.core.*
import me.ixor.sred.declarative.*
import me.ixor.sred.declarative.annotations.StateHandler
import me.ixor.sred.event.*
import me.ixor.sred.state.*
import me.ixor.sred.orchestrator.*
import me.ixor.sred.declarative.format.*
import me.ixor.sred.persistence.PersistenceAdapterFactory
import me.ixor.sred.core.logger
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.io.File

/**
 * 演示构建器模式和链式调用的使用
 * 
 * 新特性：
 * 1. StateFlow 所有方法支持链式调用（返回 this）
 * 2. EventBuilder 支持链式创建事件
 * 3. StateContextBuilder 支持链式创建上下文
 * 4. StateMachineBuilder 支持链式构建状态机
 * 5. StateOrchestratorBuilder 支持链式构建调度器
 * 6. 使用注解（装饰器）声明函数与状态的绑定
 */

// 共享的数据类定义
data class User(val userId: String, val name: String, val accountId: String)
data class Account(val accountId: String, val userId: String, var balance: Double)

/**
 * 转账状态处理器类
 * 使用 @StateHandler 注解声明函数与状态的绑定
 */
class TransferStateHandlers(
    private val usersTable: MutableMap<String, User>,
    private val accountsTable: MutableMap<String, Account>
) {
    
    private val log = logger<TransferStateHandlers>()
    
    @StateHandler(
        stateId = "validating_accounts",
        description = "验证转出和转入账户是否存在"
    )
    suspend fun validateAccounts(context: StateContext): StateResult {
        log.info { "验证账户..." }
        delay(100) // 模拟验证时间
        
        val fromUserId = context.getLocalState<String>("fromUserId")
        val toUserId = context.getLocalState<String>("toUserId")
        
        val fromUser = usersTable[fromUserId]
        val toUser = usersTable[toUserId]
        
        if (fromUser == null || toUser == null) {
            log.warn { "账户验证失败：用户不存在" }
            return StateResult.failure("账户不存在")
        } else {
            log.info { "账户验证成功" }
            log.info { "       转出账户: ${fromUser.name} (${fromUser.accountId})" }
            log.info { "       转入账户: ${toUser.name} (${toUser.accountId})" }
            return StateResult.success(mapOf(
                "fromAccountId" to fromUser.accountId,
                "toAccountId" to toUser.accountId
            ))
        }
    }
    
    @StateHandler(
        stateId = "checking_balance",
        description = "检查转出账户余额是否充足"
    )
    suspend fun checkBalance(context: StateContext): StateResult {
        log.info { "检查余额..." }
        delay(100)
        
        val amount = context.getLocalState<Number>("amount")?.toDouble() ?: 0.0
        val fromAccountId = context.getLocalState<String>("fromAccountId")
        
        val fromAccount = accountsTable[fromAccountId]
        
        if (fromAccount == null) {
            log.warn { "账户不存在" }
            return StateResult.failure("账户不存在")
        } else if (fromAccount.balance < amount) {
            log.warn { "余额不足" }
            log.warn { "       当前余额: ${fromAccount.balance}元" }
            log.warn { "       转账金额: ${amount}元" }
            return StateResult.failure("余额不足")
        } else {
            log.info { "余额检查通过" }
            log.info { "       当前余额: ${fromAccount.balance}元" }
            log.info { "       转账金额: ${amount}元" }
            log.info { "       转账后余额: ${fromAccount.balance - amount}元" }
            return StateResult.success()
        }
    }
    
    @StateHandler(
        stateId = "transferring",
        description = "执行转账操作（扣款和入账）"
    )
    suspend fun executeTransfer(context: StateContext): StateResult {
        log.info { "执行转账..." }
        delay(200) // 模拟转账处理时间
        
        val amount = context.getLocalState<Number>("amount")?.toDouble() ?: 0.0
        val fromAccountId = context.getLocalState<String>("fromAccountId")
        val toAccountId = context.getLocalState<String>("toAccountId")
        
        val fromAccount = accountsTable[fromAccountId]
        val toAccount = accountsTable[toAccountId]
        
        if (fromAccount == null || toAccount == null) {
            log.error { "转账失败：账户不存在" }
            return StateResult.failure("账户不存在")
        } else {
            // 执行转账（扣款和入账）
            fromAccount.balance -= amount
            toAccount.balance += amount
            
            log.info { "转账成功！" }
            log.info { "       转出账户余额: ${fromAccount.balance}元" }
            log.info { "       转入账户余额: ${toAccount.balance}元" }
            
            return StateResult.success(mapOf(
                "transferId" to "TXN_${System.currentTimeMillis()}",
                "transferAmount" to amount,
                "fromBalance" to fromAccount.balance,
                "toBalance" to toAccount.balance
            ))
        }
    }
    
    @StateHandler(
        stateId = "transfer_failed",
        description = "处理转账失败的情况"
    )
    suspend fun handleTransferFailed(context: StateContext): StateResult {
        log.error { "转账失败" }
        val error = context.getLocalState<String>("error")
        if (error != null) {
            log.error { "   错误信息: $error" }
        }
        return StateResult.failure(error ?: "未知错误")
    }
    
    @StateHandler(
        stateId = "transfer_success",
        description = "处理转账成功的情况"
    )
    suspend fun handleTransferSuccess(context: StateContext): StateResult {
        log.info { "转账流程完成！" }
        val transferId = context.getLocalState<String>("transferId")
        val amount = context.getLocalState<Number>("transferAmount")
        log.info { "   交易ID: $transferId" }
        log.info { "   转账金额: ${amount}元" }
        return StateResult.success()
    }
}

/**
 * SRED架构主程序入口
 * 用户转账案例：用户A -> 用户B 转账
 */
fun main(args: Array<String>) = runBlocking {
    val log = logger<Unit>()
    
    log.info { "=".repeat(60) }
    log.info { "SRED架构 - 用户转账案例" }
    log.info { "=".repeat(60) }
    
    // ========== 1. 初始化数据表（模拟数据库） ==========
    // 用户表
    val usersTable = ConcurrentHashMap<String, User>()
    usersTable["userA"] = User("userA", "张三", "accountA")
    usersTable["userB"] = User("userB", "李四", "accountB")
    
    // 账户表
    val accountsTable = ConcurrentHashMap<String, Account>()
    accountsTable["accountA"] = Account("accountA", "userA", 1000.0)
    accountsTable["accountB"] = Account("accountB", "userB", 500.0)
    
    // 创建状态处理器实例
    val transferHandlers = TransferStateHandlers(usersTable, accountsTable)
    
    log.info { "\n📊 初始账户余额：" }
    log.info { "   ${usersTable["userA"]?.name} (账户A): ${accountsTable["accountA"]?.balance}元" }
    log.info { "   ${usersTable["userB"]?.name} (账户B): ${accountsTable["accountB"]?.balance}元" }
    
    // ========== 2. 从文件加载状态流程配置 ==========
    // 支持从本地文件或URL加载配置
    // 示例：从本地文件加载 "sred.json"
    // 也可以使用URL：FormatLoader.load("http://example.com/config.json")
    val configPath = args.getOrNull(0) ?: "sred.json"
    log.info { "\n📄 加载配置文件: $configPath" }
    
    val stateFlow = try {
        FormatLoader.load(configPath)
    } catch (e: Exception) {
        log.error(e) { "加载配置文件失败: ${e.message}" }
        log.error { "💡 提示: 请确保配置文件存在，或使用以下方式之一:" }
        log.error { "      - 本地文件: FormatLoader.load(\"sred.json\")" }
        log.error { "      - 远程URL: FormatLoader.load(\"https://example.com/config.json\")" }
        throw e
    }
    log.info { "✅ 配置文件加载成功" }
    
    // ========== 3. 初始化SQLite持久化 ==========
    val dbPath = "transfer_state.db"
    // 如果数据库文件已存在，可以选择删除以重新开始
    val dbFile = File(dbPath)
    if (dbFile.exists()) {
        log.info { "发现已存在的数据库文件，使用现有数据..." }
    } else {
        log.info { "初始化SQLite持久化存储..." }
    }
    
    val persistence = PersistenceAdapterFactory.createSqliteAdapter(dbPath)
    persistence.initialize()
    
    // 使用 use 确保资源正确关闭
    persistence.use {
    
    // ========== 4. 使用注解（装饰器）绑定状态处理函数 ==========
    // 通过 @StateHandler 注解自动绑定函数到对应的状态
    stateFlow.bindAnnotatedFunctions(transferHandlers)
    
    // ========== 5. 使用链式调用构建状态机 ==========
    val stateMachine = stateFlow.build()
    
    // ========== 6. 使用构建器模式创建初始上下文（链式调用） ==========
    val transferId = "transfer_${System.currentTimeMillis()}"
    val initialContext = StateContextFactory.builder()
        .id(transferId)
        .localState("transferId", transferId)
        .localState("fromUserId", "userA")
        .localState("toUserId", "userB")
        .localState("amount", 200.0)
        .build()
    
    log.info { "启动转账流程..." }
    log.info { "   转账ID: $transferId" }
    log.info { "   转出用户: ${usersTable["userA"]?.name}" }
    log.info { "   转入用户: ${usersTable["userB"]?.name}" }
    log.info { "   转账金额: 200.0元" }
    
    // 保存初始上下文到SQLite
    persistence.saveContext(initialContext)
    log.info { "初始上下文已保存到SQLite" }
    
    // 使用StateMachine启动实例
    val instance = stateMachine.start(transferId, initialContext)
    
    // ========== 7. 逐步发送事件触发状态转移 ==========
    var currentState = instance.getCurrentState()
    var step = 1
    val stateHistory = mutableListOf<String>()
    stateHistory.add(currentState ?: "unknown")
    
    log.info { "开始状态流转..." }
    
    // 自动执行状态流转直到完成
    while (currentState != null && currentState != "transfer_success" && currentState != "transfer_failed") {
        log.info { "--- 步骤 $step: 当前状态 - $currentState ---" }
        
        // 使用构建器模式创建事件（链式调用）
        val processEvent = EventFactory.builder()
            .type("transfer", "process")
            .name("处理转账")
            .description("执行转账步骤 $step")
            .source("main")
            .payload("step", step)
            .metadata("timestamp", System.currentTimeMillis())
            .build()
        
        // 保存事件到SQLite
        persistence.saveEvent(transferId, processEvent)
        
        val result = instance.processEvent(processEvent)
        val previousState = currentState
        currentState = instance.getCurrentState()
        
        if (currentState != previousState && currentState != null) {
            stateHistory.add(currentState)
            
            // 更新上下文并保存到SQLite
            val updatedContext = instance.getContext()
            if (updatedContext != null) {
                persistence.saveContext(updatedContext)
                // 保存状态历史
                persistence.saveStateHistory(
                    contextId = transferId,
                    fromStateId = previousState,
                    toStateId = currentState,
                    eventId = processEvent.id,
                    timestamp = processEvent.timestamp
                )
                log.info { "状态转移已保存: $previousState -> $currentState" }
            }
        }
        
        if (!result.success) {
            log.error(result.error) { "处理失败: ${result.error?.message}" }
            break
        }
        
        if (currentState == "transfer_success" || currentState == "transfer_failed") {
            break
        }
        
        step++
        delay(300) // 短暂延迟以便观察
    }
    
    // ========== 8. 显示最终结果 ==========
    log.info { "=".repeat(60) }
    log.info { "📊 最终账户余额：" }
    log.info { "   ${usersTable["userA"]?.name} (账户A): ${accountsTable["accountA"]?.balance}元" }
    log.info { "   ${usersTable["userB"]?.name} (账户B): ${accountsTable["accountB"]?.balance}元" }
    
    // ========== 9. 显示状态轨迹 ==========
    log.info { "📈 状态流转轨迹：" }
    stateHistory.forEachIndexed { index, state ->
        if (index == 0) {
            log.info { "   ${index + 1}. [初始] -> $state" }
        } else {
            val fromState = stateHistory[index - 1]
            val suffix = if (index == stateHistory.size - 1) " (最终)" else ""
            log.info { "   ${index + 1}. $fromState -> $state$suffix" }
        }
    }
    
    // ========== 10. 从SQLite读取并显示持久化的数据 ==========
    log.info { "💾 从SQLite读取持久化数据：" }
    try {
        // 读取保存的上下文
        val savedContext = persistence.loadContext(transferId)
        if (savedContext != null) {
            log.info { "上下文已恢复" }
            log.info { "      当前状态ID: ${savedContext.currentStateId}" }
            log.info { "      转账ID: ${savedContext.getLocalState<String>("transferId")}" }
            log.info { "      转出用户: ${savedContext.getLocalState<String>("fromUserId")}" }
            log.info { "      转入用户: ${savedContext.getLocalState<String>("toUserId")}" }
            log.info { "      转账金额: ${savedContext.getLocalState<Number>("amount")}元" }
        } else {
            log.warn { "未找到保存的上下文" }
        }
        
        // 读取状态历史
        val history = persistence.getStateHistory(transferId)
        if (history.isNotEmpty()) {
            log.info { "📜 状态历史记录（从SQLite读取）：" }
            history.forEachIndexed { index, entry ->
                log.info { "      ${index + 1}. ${entry.fromStateId} -> ${entry.toStateId} (${entry.timestamp})" }
            }
        }
        
        // 读取保存的上下文中的最近事件
        val savedContextWithEvents = persistence.loadContext(transferId)
        if (savedContextWithEvents != null && savedContextWithEvents.recentEvents.isNotEmpty()) {
            log.info { "📨 最近事件（从SQLite读取）：" }
            savedContextWithEvents.recentEvents.take(5).forEachIndexed { index, event ->
                log.info { "      ${index + 1}. ${event.name} (${event.timestamp})" }
            }
        }
    } catch (e: Exception) {
        log.error(e) { "读取SQLite数据时出错: ${e.message}" }
    }
    
    log.info { "=".repeat(60) }
    log.info { "✅ 转账流程执行完成！" }
    log.info { "💾 所有状态数据已持久化到: $dbPath" }
    } // persistence.use 结束
}
