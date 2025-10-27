package me.ixor.sred.declarative

import me.ixor.sred.core.*
import kotlinx.coroutines.*
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.callSuspend

/**
 * 函数注册注解
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class StateHandler(
    val stateId: String,
    val description: String = ""
)

/**
 * 声明式函数注册器
 */
object FunctionRegistry {
    
    internal val registeredFunctions = mutableMapOf<String, StateFunction>()
    
    /**
     * 自动注册带注解的函数
     */
    fun registerFunctions(instance: Any) {
        val clazz = instance::class
        val functions = clazz.members.filterIsInstance<KFunction<*>>()
        
        for (function in functions) {
            val annotation = function.findAnnotation<StateHandler>()
            if (annotation != null) {
                val stateId = annotation.stateId
                val stateFunction: StateFunction = { context ->
                    try {
                        val result = function.callSuspend(instance, context)
                        result as? StateResult ?: StateResult(true)
                    } catch (e: Exception) {
                        StateResult(false, error = e)
                    }
                }
                registerFunction(stateId, stateFunction)
            }
        }
    }
    
    /**
     * 手动注册函数
     */
    fun registerFunction(stateId: String, function: StateFunction) {
        registeredFunctions[stateId] = function
    }
    
    /**
     * 获取函数
     */
    fun getFunction(stateId: String): StateFunction? = registeredFunctions[stateId]
    
    /**
     * 清空注册
     */
    fun clear() {
        registeredFunctions.clear()
    }
}

/**
 * 用户注册服务
 * 演示声明式函数绑定
 */
class UserRegistrationService {
    
    @StateHandler("registration_validating", "用户参数校验")
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
    
    @StateHandler("registration_storing", "存储用户信息")
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
                "userId" to (userId ?: ""),
                "storedEmail" to (email ?: ""),
                "storedUsername" to (username ?: "")
            ))
        } catch (e: Exception) {
            println("❌ 用户信息存储失败: ${e.message}")
            StateResult(false, error = e)
        }
    }
    
    @StateHandler("email_sending", "发送验证邮件")
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
 * 状态流构建器扩展
 */
fun StateFlow.bindFunctions(service: Any) {
    FunctionRegistry.registerFunctions(service)
    
    // 将注册的函数绑定到状态
    FunctionRegistry.registeredFunctions.forEach { (stateId, function) ->
        this.bind(stateId, function)
    }
}
