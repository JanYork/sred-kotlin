package me.ixor.sred.examples.registration

/**
 * 用户注册领域模型
 */
data class RegistrationUser(
    val username: String,
    val email: String,
    val password: String,
    var status: RegistrationStatus = RegistrationStatus.PENDING
)

enum class RegistrationStatus {
    PENDING,      // 待验证
    VERIFIED,     // 已验证
    ACTIVATED,    // 已激活
    REJECTED      // 已拒绝
}

