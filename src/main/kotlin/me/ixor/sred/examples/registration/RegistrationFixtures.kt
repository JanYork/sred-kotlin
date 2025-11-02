package me.ixor.sred.examples.registration

/**
 * 用户注册测试数据构建器
 */
object RegistrationFixtures {
    fun createContext(): RegistrationContext {
        val users = mutableMapOf<String, RegistrationUser>()
        val verificationCodes = mutableMapOf<String, String>() // email -> code
        return RegistrationContext(users, verificationCodes)
    }
}

/**
 * 用户注册上下文
 */
data class RegistrationContext(
    val users: MutableMap<String, RegistrationUser>,
    val verificationCodes: MutableMap<String, String>
)

