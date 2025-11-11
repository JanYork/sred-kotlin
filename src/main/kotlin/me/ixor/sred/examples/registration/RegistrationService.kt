package me.ixor.sred.examples.registration

import me.ixor.sred.core.*
import me.ixor.sred.declarative.StateResult
import me.ixor.sred.declarative.annotations.StateHandler
import me.ixor.sred.core.logger
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * 用户注册服务 - 状态处理器
 */
class RegistrationService(
    private val users: MutableMap<String, RegistrationUser>,
    private val verificationCodes: MutableMap<String, String>
) {
    private val log = logger<RegistrationService>()
    
    @StateHandler(RegistrationStates.VALIDATING, "验证用户信息")
    suspend fun validateUser(context: StateContext): StateResult {
        log.info { "  ✓ 验证用户信息" }
        delay(100)
        
        val username = context.getLocalState<String>("username") ?: ""
        val email = context.getLocalState<String>("email") ?: ""
        val password = context.getLocalState<String>("password") ?: ""
        
        return when {
            username.isEmpty() -> StateResult.failure("用户名不能为空")
            email.isEmpty() || !email.contains("@") -> StateResult.failure("邮箱格式不正确")
            password.length < 6 -> StateResult.failure("密码长度至少6位")
            users.containsKey(username) -> StateResult.failure("用户名已存在")
            else -> {
                users[username] = RegistrationUser(username, email, password)
                StateResult.success(mapOf(
                    "validated" to true
                ))
            }
        }
    }
    
    @StateHandler(RegistrationStates.SENDING_EMAIL, "发送验证邮件")
    suspend fun sendEmail(context: StateContext): StateResult {
        log.info { "  ✓ 发送验证邮件" }
        delay(200)
        
        val email = context.getLocalState<String>("email") ?: ""
        val code = generateVerificationCode()
        verificationCodes[email] = code
        
        return StateResult.success(mapOf(
            "verificationCode" to code,
            "emailSent" to true
        ))
    }
    
    @StateHandler(RegistrationStates.VERIFYING_CODE, "验证验证码")
    suspend fun verifyCode(context: StateContext): StateResult {
        log.info { "  ✓ 验证验证码" }
        delay(100)
        
        val email = context.getLocalState<String>("email") ?: ""
        val inputCode = context.getLocalState<String>("inputCode") ?: ""
        val expectedCode = verificationCodes[email]
        // 开发期调试日志：对比期望与输入（如需隐藏可改为部分掩码）
        log.debug { "验证调试: email=$email, expected=$expectedCode, input=$inputCode" }
        
        return if (expectedCode == null) {
            StateResult.failure("验证码已过期")
        } else if (inputCode != expectedCode) {
            StateResult.failure("验证码不正确")
        } else {
            StateResult.success(mapOf("verified" to true))
        }
    }
    
    @StateHandler(RegistrationStates.ACTIVATING, "激活账户")
    suspend fun activateAccount(context: StateContext): StateResult {
        log.info { "  ✓ 激活账户" }
        delay(150)
        
        val username = context.getLocalState<String>("username") ?: ""
        val user = users[username]
        
        return if (user == null) {
            StateResult.failure("用户不存在")
        } else {
            user.status = RegistrationStatus.ACTIVATED
            StateResult.success(mapOf(
                "activated" to true,
                "userId" to "USER_${System.currentTimeMillis()}"
            ))
        }
    }
    
    @StateHandler(RegistrationStates.FAILED, "处理失败")
    suspend fun handleFailed(context: StateContext): StateResult {
        log.error { "  ✗ 注册失败" }
        val error = context.getLocalState<String>("error") ?: "未知错误"
        return StateResult.failure(error)
    }
    
    @StateHandler(RegistrationStates.SUCCESS, "注册成功")
    suspend fun handleSuccess(context: StateContext): StateResult {
        log.info { "  ✓ 注册成功" }
        return StateResult.success()
    }
    
    private fun generateVerificationCode(): String {
        return (100000..999999).random().toString()
    }
}

