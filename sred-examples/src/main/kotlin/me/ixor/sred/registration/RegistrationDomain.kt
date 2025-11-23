package me.ixor.sred.registration

/**
 * 用户注册领域模型
 *
 * 放在独立的 registration 包中，避免与示例代码（examples）混淆，
 * 便于在 API、应用层重复使用。
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

