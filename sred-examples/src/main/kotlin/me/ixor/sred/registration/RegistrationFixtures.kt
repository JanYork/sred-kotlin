package me.ixor.sred.registration

/**
 * 用户注册测试数据构建器
 *
 * 在示例或本地开发场景下，提供基于内存仓储的上下文对象。
 * 生产环境中由 Spring 管理实际的仓储与服务实例。
 */
object RegistrationFixtures {
    fun createContext(): RegistrationContext {
        val userRepository = InMemoryRegistrationUserRepository()
        val verificationCodeRepository = InMemoryVerificationCodeRepository()
        return RegistrationContext(userRepository, verificationCodeRepository)
    }
}

/**
 * 用户注册上下文（示例用）
 */
data class RegistrationContext(
    val userRepository: RegistrationUserRepository,
    val verificationCodeRepository: VerificationCodeRepository
)

