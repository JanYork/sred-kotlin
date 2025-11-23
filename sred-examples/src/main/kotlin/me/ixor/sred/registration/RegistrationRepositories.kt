package me.ixor.sred.registration

import java.util.concurrent.ConcurrentHashMap

/**
 * 用户注册仓储接口定义。
 * 当前提供内存实现，后续可以替换为数据库等持久化实现。
 */
interface RegistrationUserRepository {
    fun findByUsername(username: String): RegistrationUser?
    fun existsByUsername(username: String): Boolean
    fun save(user: RegistrationUser)
}

interface VerificationCodeRepository {
    fun saveCode(email: String, code: String)
    fun findCode(email: String): String?
}

/**
 * 基于 ConcurrentHashMap 的内存实现，线程安全，适合作为示例或测试实现。
 */
class InMemoryRegistrationUserRepository(
    private val users: MutableMap<String, RegistrationUser> = ConcurrentHashMap()
) : RegistrationUserRepository {

    override fun findByUsername(username: String): RegistrationUser? = users[username]

    override fun existsByUsername(username: String): Boolean = users.containsKey(username)

    override fun save(user: RegistrationUser) {
        users[user.username] = user
    }

    /**
     * 仅供示例或测试查询底层数据使用，不在领域服务中依赖。
     */
    fun allUsers(): Map<String, RegistrationUser> = users.toMap()
}

class InMemoryVerificationCodeRepository(
    private val codes: MutableMap<String, String> = ConcurrentHashMap()
) : VerificationCodeRepository {

    override fun saveCode(email: String, code: String) {
        codes[email] = code
    }

    override fun findCode(email: String): String? = codes[email]

    /**
     * 仅供示例或测试查询底层数据使用。
     */
    fun allCodes(): Map<String, String> = codes.toMap()
}
