package me.ixor.sred.examples.registration

import me.ixor.sred.core.logger

/**
 * 用户注册视图 - 负责显示和格式化输出
 */
object RegistrationView {
    private val log = logger<RegistrationView>()
    
    fun showRegistrationInfo(username: String, email: String) {
        log.info { "用户注册: username=$username, email=$email" }
    }
    
    fun showVerificationCode(email: String, code: String) {
        log.info { "验证码已发送到 $email: $code" }
    }
    
    fun showResult(username: String, success: Boolean) {
        if (success) {
            log.info { "✅ 用户注册成功: $username" }
        } else {
            log.error { "❌ 用户注册失败: $username" }
        }
    }
}

