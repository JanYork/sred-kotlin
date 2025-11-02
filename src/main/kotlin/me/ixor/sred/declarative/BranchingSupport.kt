package me.ixor.sred.declarative

import me.ixor.sred.core.*

/**
 * 执行模式枚举
 * 基于论文中的状态轮转思想，支持不同的执行编排方式
 */
enum class ExecutionMode {
    /**
     * 顺序执行：状态按顺序依次执行
     * 适用于线性业务流程
     */
    SEQUENTIAL,
    
    /**
     * 并行执行：多个状态同时执行
     * 适用于独立的任务分支，如并行审核、并发查询等
     */
    PARALLEL,
    
    /**
     * 条件分支：根据上下文条件选择执行路径
     * 基于论文中的上下文驱动模型：s' = T(s, e, C)
     */
    CONDITIONAL,
    
    /**
     * 分支合并：多个分支执行完成后合并
     * 通常与PARALLEL配合使用
     */
    JOIN
}

/**
 * 分支配置
 * 定义条件分支的评估规则和执行目标
 */
data class BranchConfiguration(
    /**
     * 分支名称
     */
    val name: String,
    
    /**
     * 目标状态ID
     */
    val targetStateId: String,
    
    /**
     * 分支条件评估器
     * 基于上下文进行条件判断：使用 ContextConditionEvaluator
     */
    val condition: ContextConditionEvaluator,
    
    /**
     * 分支优先级（数值越大优先级越高）
     * 当多个分支条件都满足时，选择优先级最高的
     */
    val priority: Int = 0,
    
    /**
     * 分支描述
     */
    val description: String = "",
    
    /**
     * 分支元数据
     */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 并行执行配置
 * 定义并行执行的状态集合
 */
data class ParallelConfiguration(
    /**
     * 并行分支列表
     */
    val branches: List<ParallelBranch>,
    
    /**
     * 等待策略：所有分支完成 vs 任一分支完成
     */
    val waitStrategy: ParallelWaitStrategy = ParallelWaitStrategy.ALL,
    
    /**
     * 超时时间（秒），null 表示不超时
     */
    val timeout: Long? = null,
    
    /**
     * 错误处理策略
     */
    val errorStrategy: ParallelErrorStrategy = ParallelErrorStrategy.FAIL_ALL
)

/**
 * 并行分支
 */
data class ParallelBranch(
    /**
     * 分支ID
     */
    val branchId: String,
    
    /**
     * 目标状态ID
     */
    val targetStateId: String,
    
    /**
     * 分支描述
     */
    val description: String = "",
    
    /**
     * 分支元数据
     */
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 并行等待策略
 */
enum class ParallelWaitStrategy {
    /**
     * 等待所有分支完成
     */
    ALL,
    
    /**
     * 任一分支完成即可
     */
    ANY,
    
    /**
     * 等待指定数量的分支完成
     */
    N_COUNT
}

/**
 * 并行错误处理策略
 */
enum class ParallelErrorStrategy {
    /**
     * 任一分支失败，整体失败
     */
    FAIL_ALL,
    
    /**
     * 忽略失败分支，继续执行成功分支
     */
    IGNORE_FAILURES,
    
    /**
     * 容错模式：失败分支达到阈值时整体失败
     */
    TOLERATE_FAILURES
}

/**
 * 上下文条件评估器
 * 
 * 基于论文中的上下文驱动模型实现：
 * 上下文 C = (Σ_local, Σ_global, E_recent, M)
 * 
 * 条件评估器利用上下文中的局部状态、全局状态、最近事件和元信息
 * 进行复杂的分支判断
 */
interface ContextConditionEvaluator {
    /**
     * 评估条件是否满足
     * 
     * @param currentState 当前状态
     * @param event 触发事件
     * @param context 状态上下文
     * @return 条件是否满足
     */
    fun evaluate(
        currentState: State,
        event: Event,
        context: StateContext
    ): Boolean
}

/**
 * 简单上下文条件评估器
 * 使用Lambda表达式进行条件评估
 */
class SimpleContextConditionEvaluator(
    private val predicate: (State, Event, StateContext) -> Boolean
) : ContextConditionEvaluator {
    override fun evaluate(
        currentState: State,
        event: Event,
        context: StateContext
    ): Boolean {
        return predicate(currentState, event, context)
    }
}

/**
 * 表达式条件评估器
 * 支持基于JSONPath或简单表达式的条件评估
 */
class ExpressionConditionEvaluator(
    /**
     * 表达式类型
     */
    private val expressionType: ExpressionType,
    
    /**
     * 表达式内容
     */
    private val expression: String
) : ContextConditionEvaluator {
    
    override fun evaluate(
        currentState: State,
        event: Event,
        context: StateContext
    ): Boolean {
        return when (expressionType) {
            ExpressionType.JSON_PATH -> evaluateJsonPath(context)
            ExpressionType.SIMPLE -> evaluateSimple(expression, context)
            ExpressionType.GROOVY -> evaluateGroovy(expression, context)
        }
    }
    
    /**
     * 评估JSONPath表达式
     */
    private fun evaluateJsonPath(context: StateContext): Boolean {
        // TODO: 实现JSONPath表达式评估
        // 例如：$.localState.amount > 1000
        return false
    }
    
    /**
     * 评估简单表达式
     */
    private fun evaluateSimple(expression: String, context: StateContext): Boolean {
        // TODO: 实现简单表达式评估
        // 例如：amount > 1000 && userType == "VIP"
        return false
    }
    
    /**
     * 评估Groovy表达式（如果需要）
     */
    private fun evaluateGroovy(expression: String, context: StateContext): Boolean {
        // TODO: 实现Groovy表达式评估（可选）
        return false
    }
    
    enum class ExpressionType {
        JSON_PATH,
        SIMPLE,
        GROOVY
    }
}

/**
 * 上下文条件评估器构建器
 */
object ContextConditionEvaluatorBuilder {
    /**
     * 创建基于Lambda的评估器
     */
    fun create(predicate: (State, Event, StateContext) -> Boolean): ContextConditionEvaluator {
        return SimpleContextConditionEvaluator(predicate)
    }
    
    /**
     * 创建基于表达式的评估器
     */
    fun createExpression(
        expressionType: ExpressionConditionEvaluator.ExpressionType,
        expression: String
    ): ContextConditionEvaluator {
        return ExpressionConditionEvaluator(expressionType, expression)
    }
    
    /**
     * 创建基于局部状态的条件评估器
     * 例如：检查 localState 中的某个值
     */
    fun createLocalStateCondition(
        key: String,
        operator: ComparisonOperator,
        value: Any
    ): ContextConditionEvaluator {
        return SimpleContextConditionEvaluator { _, _, context ->
            val localValue = context.localState[key]
            operator.compare(localValue, value)
        }
    }
    
    /**
     * 创建基于全局状态的条件评估器
     */
    fun createGlobalStateCondition(
        key: String,
        operator: ComparisonOperator,
        value: Any
    ): ContextConditionEvaluator {
        return SimpleContextConditionEvaluator { _, _, context ->
            val globalValue = context.globalState[key]
            operator.compare(globalValue, value)
        }
    }
    
    /**
     * 创建组合条件评估器（AND/OR）
     */
    fun createComposite(
        operator: LogicalOperator,
        vararg conditions: ContextConditionEvaluator
    ): ContextConditionEvaluator {
        return SimpleContextConditionEvaluator { state, event, context ->
            when (operator) {
                LogicalOperator.AND -> conditions.all { it.evaluate(state, event, context) }
                LogicalOperator.OR -> conditions.any { it.evaluate(state, event, context) }
                LogicalOperator.NOT -> !conditions.first().evaluate(state, event, context)
            }
        }
    }
}

/**
 * 比较操作符
 */
enum class ComparisonOperator {
    EQ,     // 等于
    NE,     // 不等于
    GT,     // 大于
    GE,     // 大于等于
    LT,     // 小于
    LE,     // 小于等于
    IN,     // 包含
    CONTAINS; // 字符串包含
    
    @Suppress("UNCHECKED_CAST")
    fun compare(actual: Any?, expected: Any?): Boolean {
        return when (this) {
            EQ -> actual == expected
            NE -> actual != expected
            GT -> (actual as? Comparable<Any>)?.compareTo(expected ?: return false) ?: 0 > 0
            GE -> (actual as? Comparable<Any>)?.compareTo(expected ?: return false) ?: 0 >= 0
            LT -> (actual as? Comparable<Any>)?.compareTo(expected ?: return false) ?: 0 < 0
            LE -> (actual as? Comparable<Any>)?.compareTo(expected ?: return false) ?: 0 <= 0
            IN -> {
                when (expected) {
                    is Collection<*> -> expected.contains(actual)
                    is Array<*> -> expected.contains(actual)
                    else -> false
                }
            }
            CONTAINS -> {
                when {
                    actual is String && expected is String -> actual.contains(expected)
                    actual is Collection<*> -> actual.contains(expected)
                    else -> false
                }
            }
        }
    }
}

/**
 * 逻辑操作符
 */
enum class LogicalOperator {
    AND,
    OR,
    NOT
}

