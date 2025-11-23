package me.ixor.sred.examples

import me.ixor.sred.core.*
import me.ixor.sred.declarative.*
import kotlinx.coroutines.runBlocking

/**
 * 分支和并行执行示例
 * 演示如何使用条件分支和并行执行功能
 */
object BranchingExample {
    
    /**
     * 示例1：条件分支 - 根据金额选择审核流程
     */
    fun createAmountBasedFlow(): StateFlow {
        return StateFlow()
            .config(pauseable = true, defaultTimeout = 300)
            .state("init", "初始状态", type = StateFlow.StateType.INITIAL, isInitial = true)
            .state("validate", "验证订单")
            .conditionalState(
                id = "amount_check",
                name = "金额检查",
                branches = listOf(
                    BranchConfiguration(
                        name = "大额审核路径",
                        targetStateId = "large_amount_audit",
                        condition = ContextConditionEvaluatorBuilder.createLocalStateCondition(
                            key = "amount",
                            operator = ComparisonOperator.GE,
                            value = 10000
                        ),
                        priority = 2,
                        description = "金额大于等于10000时走大额审核"
                    ),
                    BranchConfiguration(
                        name = "小额审核路径",
                        targetStateId = "small_amount_audit",
                        condition = ContextConditionEvaluatorBuilder.createLocalStateCondition(
                            key = "amount",
                            operator = ComparisonOperator.LT,
                            value = 10000
                        ),
                        priority = 1,
                        description = "金额小于10000时走小额审核"
                    )
                )
            )
            .state("large_amount_audit", "大额审核")
            .state("small_amount_audit", "小额审核")
            .state("completed", "完成", type = StateFlow.StateType.FINAL, isFinal = true)
            // 定义转移
            .transition("init", "validate")
            .transition("validate", "amount_check")
            .transition("large_amount_audit", "completed")
            .transition("small_amount_audit", "completed")
    }
    
    /**
     * 示例2：并行执行 - 并行审核
     */
    fun createParallelAuditFlow(): StateFlow {
        return StateFlow()
            .config(pauseable = false, defaultTimeout = 3600)
            .state("init", "初始状态", type = StateFlow.StateType.INITIAL, isInitial = true)
            .parallelState(
                id = "parallel_audit",
                name = "并行审核",
                parallelConfig = ParallelConfiguration(
                    branches = listOf(
                        ParallelBranch("audit1", "audit_state_1", "审核员1审核"),
                        ParallelBranch("audit2", "audit_state_2", "审核员2审核"),
                        ParallelBranch("audit3", "audit_state_3", "审核员3审核")
                    ),
                    waitStrategy = ParallelWaitStrategy.ALL,
                    timeout = 3600,  // 1小时超时
                    errorStrategy = ParallelErrorStrategy.FAIL_ALL
                )
            )
            .state("audit_state_1", "审核员1审核")
            .state("audit_state_2", "审核员2审核")
            .state("audit_state_3", "审核员3审核")
            .joinState("audit_join", "合并审核结果")
            .state("completed", "完成", type = StateFlow.StateType.FINAL, isFinal = true)
            // 定义转移
            .transition("init", "parallel_audit")
            .transition("parallel_audit", "audit_join")
            .transition("audit_join", "completed")
    }
    
    /**
     * 示例3：复杂条件 - VIP用户特殊处理
     */
    fun createVIPFlow(): StateFlow {
        return StateFlow()
            .config(pauseable = true)
            .state("init", "初始状态", type = StateFlow.StateType.INITIAL, isInitial = true)
            .conditionalState(
                id = "user_type_check",
                name = "用户类型检查",
                branches = listOf(
                    BranchConfiguration(
                        name = "VIP大额用户",
                        targetStateId = "vip_large_process",
                        condition = ContextConditionEvaluatorBuilder.createComposite(
                            operator = LogicalOperator.AND,
                            ContextConditionEvaluatorBuilder.createLocalStateCondition(
                                key = "userType",
                                operator = ComparisonOperator.EQ,
                                value = "VIP"
                            ),
                            ContextConditionEvaluatorBuilder.createLocalStateCondition(
                                key = "amount",
                                operator = ComparisonOperator.GE,
                                value = 50000
                            )
                        ),
                        priority = 3,
                        description = "VIP用户且金额大于等于50000"
                    ),
                    BranchConfiguration(
                        name = "VIP普通金额",
                        targetStateId = "vip_normal_process",
                        condition = ContextConditionEvaluatorBuilder.createComposite(
                            operator = LogicalOperator.AND,
                            ContextConditionEvaluatorBuilder.createLocalStateCondition(
                                key = "userType",
                                operator = ComparisonOperator.EQ,
                                value = "VIP"
                            ),
                            ContextConditionEvaluatorBuilder.createLocalStateCondition(
                                key = "amount",
                                operator = ComparisonOperator.LT,
                                value = 50000
                            )
                        ),
                        priority = 2,
                        description = "VIP用户但金额小于50000"
                    ),
                    BranchConfiguration(
                        name = "普通用户",
                        targetStateId = "normal_process",
                        condition = ContextConditionEvaluatorBuilder.createLocalStateCondition(
                            key = "userType",
                            operator = ComparisonOperator.NE,
                            value = "VIP"
                        ),
                        priority = 1,
                        description = "非VIP用户"
                    )
                )
            )
            .state("vip_large_process", "VIP大额处理")
            .state("vip_normal_process", "VIP普通处理")
            .state("normal_process", "普通处理")
            .state("completed", "完成", type = StateFlow.StateType.FINAL, isFinal = true)
            // 定义转移
            .transition("init", "user_type_check")
            .transition("vip_large_process", "completed")
            .transition("vip_normal_process", "completed")
            .transition("normal_process", "completed")
    }
    
    /**
     * 运行示例
     */
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 分支和并行执行示例 ===\n")
        
        // 示例1：条件分支
        println("示例1：条件分支 - 根据金额选择审核流程")
        val amountFlow = createAmountBasedFlow()
        val amountMachine = amountFlow.build()
        
        val instance1 = amountMachine.start(
            "order_001",
            StateContextFactory.create(
                localState = mapOf("amount" to 15000, "orderId" to "order_001")
            )
        )

        println("订单金额: 15000")
        val event = EventFactory.createSimpleEvent("process", mapOf<String, Any>())

        // 第一次：init -> validate
        instance1.processEvent(event)
        // 第二次：validate -> amount_check
        instance1.processEvent(event)
        // 第三次：在 amount_check 上执行条件分支，才会选出分支
        val result1 = instance1.processEvent(event)

        println("执行结果: ${result1.success}")
        println("当前状态: ${instance1.getCurrentState()}")
        println("选择的分支: ${result1.data["selectedBranch"]}\n")
        
        // 示例2：并行执行
        println("示例2：并行执行 - 并行审核")
        val parallelFlow = createParallelAuditFlow()
        val parallelMachine = parallelFlow.build()
        
        val instance2 = parallelMachine.start(
            "audit_001",
            StateContextFactory.create()
        )

        val parallelEvent = EventFactory.createSimpleEvent("start_audit", mapOf<String, Any>())

        // 第一次：init -> parallel_audit
        instance2.processEvent(parallelEvent)
        // 第二次：在 parallel_audit 上执行并行分支
        val result2 = instance2.processEvent(parallelEvent)

        println("执行结果: ${result2.success}")
        println("当前状态: ${instance2.getCurrentState()}")
        println("并行结果数: ${result2.data["parallelResults"]}\n")
        
        // 示例3：复杂条件
        println("示例3：复杂条件 - VIP用户特殊处理")
        val vipFlow = createVIPFlow()
        val vipMachine = vipFlow.build()
        
        val instance3 = vipMachine.start(
            "vip_order_001",
            StateContextFactory.create(
                localState = mapOf("userType" to "VIP", "amount" to 60000)
            )
        )

        println("用户类型: VIP, 金额: 60000")
        val vipEvent = EventFactory.createSimpleEvent("process", mapOf<String, Any>())

        // 第一次：init -> user_type_check 之前的初始流转
        instance3.processEvent(vipEvent)
        // 第二次：推进到 user_type_check
        instance3.processEvent(vipEvent)
        // 第三次：在 user_type_check 上执行条件判断，选出分支
        val result3 = instance3.processEvent(vipEvent)

        println("执行结果: ${result3.success}")
        println("当前状态: ${instance3.getCurrentState()}")
        println("选择的分支: ${result3.data["selectedBranch"]}\n")
    }
}
