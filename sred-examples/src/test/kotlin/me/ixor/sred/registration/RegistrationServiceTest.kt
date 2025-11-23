package me.ixor.sred.registration

import kotlinx.coroutines.runBlocking
import me.ixor.sred.core.StateContextFactory
import me.ixor.sred.declarative.StateResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 针对 RegistrationService 的最小行为回归测试，
 * 确保后续重构（如包名迁移、示例与程序解耦）不改变核心业务规则。
 */
class RegistrationServiceTest {

    @Test
    fun `validateUser should fail when email is invalid`() = runBlocking {
        val userRepository = InMemoryRegistrationUserRepository()
        val verificationCodeRepository = InMemoryVerificationCodeRepository()
        val service = RegistrationService(userRepository, verificationCodeRepository)

        val context = StateContextFactory.create(
            localState = mapOf(
                "username" to "tester",
                "email" to "invalid-email",
                "password" to "password123"
            )
        )

        val result = service.validateUser(context)

        assertTrue(result is StateResult.Failure)
        assertTrue((result as StateResult.Failure).message.contains("邮箱"))
        assertTrue(userRepository.allUsers().isEmpty())
    }

    @Test
    fun `validateUser should succeed with valid inputs`() = runBlocking {
        val userRepository = InMemoryRegistrationUserRepository()
        val verificationCodeRepository = InMemoryVerificationCodeRepository()
        val service = RegistrationService(userRepository, verificationCodeRepository)

        val context = StateContextFactory.create(
            localState = mapOf(
                "username" to "tester",
                "email" to "tester@example.com",
                "password" to "password123"
            )
        )

        val result = service.validateUser(context)

        assertTrue(result is StateResult.Success)
        val users = userRepository.allUsers()
        assertTrue(users.containsKey("tester"))
        assertEquals("tester@example.com", users["tester"]?.email)
    }

    @Test
    fun `sendEmail should generate verification code`() = runBlocking {
        val userRepository = InMemoryRegistrationUserRepository()
        val verificationCodeRepository = InMemoryVerificationCodeRepository()
        val service = RegistrationService(userRepository, verificationCodeRepository)

        val context = StateContextFactory.create(
            localState = mapOf(
                "username" to "tester",
                "email" to "tester@example.com",
                "password" to "password123"
            )
        )

        val result = service.sendEmail(context)

        assertTrue(result is StateResult.Success)
        val code = verificationCodeRepository.findCode("tester@example.com")
        assertFalse(code.isNullOrBlank())
    }
}
