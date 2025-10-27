package me.ixor.sred.declarative

import me.ixor.sred.core.*
import kotlinx.coroutines.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import java.util.concurrent.ConcurrentHashMap

/**
 * 函数绑定DSL
 * 支持声明式函数绑定到状态
 */
class FunctionBindingDSL {
    
    private val functions = ConcurrentHashMap<String, StateFunction>()
    
    /**
     * 绑定函数到状态
     */
    fun bind(stateId: String, function: StateFunction) {
        functions[stateId] = function
    }
    
    /**
     * 绑定带注解的函数
     */
    fun bindAnnotated(instance: Any) {
        val clazz = instance::class
        val methods = clazz.members.filterIsInstance<KFunction<*>>()
        
        for (method in methods) {
            val annotation = method.findAnnotation<StateHandler>()
            if (annotation != null) {
                val stateId = annotation.stateId
                val stateFunction: StateFunction = { context ->
                    try {
                        val result = method.callSuspend(instance, context)
                        result as? StateResult ?: StateResult(true)
                    } catch (e: Exception) {
                        StateResult(false, error = e)
                    }
                }
                functions[stateId] = stateFunction
            }
        }
    }
    
    /**
     * 绑定多个函数
     */
    fun bindAll(vararg bindings: Pair<String, StateFunction>) {
        bindings.forEach { (stateId, function) ->
            functions[stateId] = function
        }
    }
    
    /**
     * 获取所有绑定的函数
     */
    fun getAllFunctions(): Map<String, StateFunction> = functions.toMap()
    
    /**
     * 清空绑定
     */
    fun clear() {
        functions.clear()
    }
}

/**
 * 函数绑定扩展函数
 */
fun StateFlow.bindFunctions(binding: FunctionBindingDSL.() -> Unit) {
    val dsl = FunctionBindingDSL()
    dsl.binding()
    
    // 将绑定的函数注册到状态流
    dsl.getAllFunctions().forEach { (stateId, function) ->
        this.bind(stateId, function)
    }
}

/**
 * 直接绑定函数到状态流
 */
fun StateFlow.bindFunction(stateId: String, function: StateFunction) {
    this.bind(stateId, function)
}

/**
 * 批量绑定函数
 */
fun StateFlow.bindFunctions(vararg bindings: Pair<String, StateFunction>) {
    bindings.forEach { (stateId, function) ->
        this.bind(stateId, function)
    }
}

/**
 * 声明式函数定义
 * 支持更简洁的函数定义语法
 */
object DeclarativeFunctions {
    
    /**
     * 用户参数校验函数
     */
    suspend fun validateUser(context: StateContext): StateResult {
        val email = context.localState["email"] as? String
        val password = context.localState["password"] as? String
        val username = context.localState["username"] as? String
        
        println("🔍 正在校验用户参数...")
        delay(100) // 模拟校验时间
        
        return when {
            email.isNullOrBlank() -> StateResult(false, error = IllegalArgumentException("邮箱不能为空"))
            !email.contains("@") -> StateResult(false, error = IllegalArgumentException("邮箱格式不正确"))
            password.isNullOrBlank() -> StateResult(false, error = IllegalArgumentException("密码不能为空"))
            password.length < 6 -> StateResult(false, error = IllegalArgumentException("密码长度不能少于6位"))
            username.isNullOrBlank() -> StateResult(false, error = IllegalArgumentException("用户名不能为空"))
            else -> {
                println("✅ 用户参数校验通过")
                StateResult(true, mapOf(
                    "validatedEmail" to email,
                    "validatedPassword" to password,
                    "validatedUsername" to username
                ))
            }
        }
    }
    
    /**
     * 存储用户信息函数
     */
    suspend fun storeUser(context: StateContext): StateResult {
        val email = context.localState["validatedEmail"] as? String
        val password = context.localState["validatedPassword"] as? String
        val username = context.localState["validatedUsername"] as? String
        
        println("💾 正在存储用户信息...")
        delay(200) // 模拟存储时间
        
        return try {
            // 模拟存储操作
            val userId = "user_${System.currentTimeMillis()}"
            println("✅ 用户信息存储成功，用户ID: $userId")
            StateResult(true, mapOf(
                "userId" to userId,
                "storedEmail" to (email ?: ""),
                "storedUsername" to (username ?: "")
            ))
        } catch (e: Exception) {
            println("❌ 用户信息存储失败: ${e.message}")
            StateResult(false, error = e)
        }
    }
    
    /**
     * 发送验证邮件函数
     */
    suspend fun sendVerificationEmail(context: StateContext): StateResult {
        val email = context.localState["storedEmail"] as? String ?: ""
        val userId = context.localState["userId"] as? String ?: ""
        
        println("📧 正在发送验证邮件到: $email")
        
        // 异步发送邮件，不等待结果
        GlobalScope.launch {
            try {
                delay(1000) // 模拟邮件发送时间
                println("✅ 验证邮件发送成功")
            } catch (e: Exception) {
                println("❌ 验证邮件发送失败: ${e.message}")
            }
        }
        
        // 立即返回成功，不等待邮件发送完成
        return StateResult(true, mapOf(
            "emailSent" to true,
            "verificationToken" to "token_${System.currentTimeMillis()}"
        ))
    }
}

/**
 * 函数引用绑定
 * 支持直接绑定函数引用
 */
fun StateFlow.bindFunctionRef(stateId: String, functionRef: suspend (StateContext) -> StateResult) {
    this.bind(stateId, functionRef)
}

/**
 * 声明式状态流构建器
 * 集成函数绑定功能
 */
class DeclarativeStateFlowBuilder {
    
    private val flow = StateFlow()
    private val functionBinding = FunctionBindingDSL()
    
    /**
     * 定义状态
     */
    fun state(
        id: String,
        name: String,
        type: StateFlow.StateType = StateFlow.StateType.NORMAL,
        parentId: String? = null,
        isInitial: Boolean = false,
        isFinal: Boolean = false,
        isError: Boolean = false
    ) {
        flow.state(id, name, type, parentId, isInitial, isFinal, isError)
    }
    
    /**
     * 定义转移
     */
    fun transition(
        from: String,
        to: String,
        condition: StateFlow.TransitionCondition = StateFlow.TransitionCondition.Success,
        priority: Int = 0
    ) {
        flow.transition(from, to, condition, priority)
    }
    
    /**
     * 绑定函数到状态
     */
    fun bind(stateId: String, function: StateFunction) {
        functionBinding.bind(stateId, function)
    }
    
    /**
     * 绑定函数引用
     */
    fun bindFunction(stateId: String, functionRef: suspend (StateContext) -> StateResult) {
        functionBinding.bind(stateId, functionRef)
    }
    
    /**
     * 构建状态流
     */
    fun build(): StateFlow {
        // 将函数绑定到状态流
        functionBinding.getAllFunctions().forEach { (stateId, function) ->
            flow.bind(stateId, function)
        }
        return flow
    }
}

/**
 * 创建声明式状态流构建器
 */
fun createDeclarativeStateFlow(builder: DeclarativeStateFlowBuilder.() -> Unit): StateFlow {
    val stateFlowBuilder = DeclarativeStateFlowBuilder()
    stateFlowBuilder.builder()
    return stateFlowBuilder.build()
}
