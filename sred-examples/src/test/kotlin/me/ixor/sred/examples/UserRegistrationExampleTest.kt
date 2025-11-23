package me.ixor.sred.examples

import me.ixor.sred.core.*
import me.ixor.sred.event.*
import me.ixor.sred.state.*
import me.ixor.sred.orchestrator.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlinx.coroutines.*

class UserRegistrationExampleTest {
    
    @Test
    fun `test user registration states creation`() {
        val states = UserRegistrationExample.createStates()
        
        assertEquals(6, states.size)
        assertTrue(states.any { it.id == UserRegistrationExample.PENDING_VALIDATION })
        assertTrue(states.any { it.id == UserRegistrationExample.VALIDATION_IN_PROGRESS })
        assertTrue(states.any { it.id == UserRegistrationExample.PENDING_VERIFICATION })
        assertTrue(states.any { it.id == UserRegistrationExample.VERIFICATION_IN_PROGRESS })
        assertTrue(states.any { it.id == UserRegistrationExample.COMPLETED })
        assertTrue(states.any { it.id == UserRegistrationExample.FAILED })
    }
    
    @Test
    fun `test user registration transitions creation`() {
        val transitions = UserRegistrationExample.createTransitions()
        
        assertTrue(transitions.isNotEmpty())
        assertTrue(transitions.any { 
            it.fromStateId == UserRegistrationExample.PENDING_VALIDATION && 
            it.toStateId == UserRegistrationExample.VALIDATION_IN_PROGRESS 
        })
        assertTrue(transitions.any { 
            it.fromStateId == UserRegistrationExample.VALIDATION_IN_PROGRESS && 
            it.toStateId == UserRegistrationExample.PENDING_VERIFICATION 
        })
    }
    
    @Test
    fun `test user registration flow`() = runBlocking {
        // 创建组件
        val eventBus = EventBusFactory.create()
        val stateManager = StateManagerFactory.create()
        val orchestrator = StateOrchestratorFactory.create(stateManager, eventBus)
        
        try {
            // 启动系统
            orchestrator.start()
            
            // 注册状态
            UserRegistrationExample.createStates().forEach { state ->
                stateManager.stateRegistry.registerState(state)
            }
            
            // 注册状态转移
            UserRegistrationExample.createTransitions().forEach { transition ->
                orchestrator.registerTransition(transition)
            }
            
            // 创建初始上下文
            val initialContext = StateContextFactory.create(
                localState = mapOf(
                    "sessionId" to "session_123",
                    "timestamp" to java.time.Instant.now()
                )
            )
            
            // 设置初始状态
            val setStateResult = stateManager.setCurrentState(
                UserRegistrationExample.PENDING_VALIDATION, 
                initialContext
            )
            assertTrue(setStateResult.success)
            assertEquals(UserRegistrationExample.PENDING_VALIDATION, stateManager.getCurrentState()?.id)
            
            // 测试用户注册请求事件
            val registrationEvent = EventFactory.create(
                type = UserRegistrationExample.USER_REGISTRATION_REQUESTED,
                name = "用户注册请求",
                description = "用户提交了注册表单",
                payload = mapOf(
                    "email" to "user@example.com",
                    "password" to "password123",
                    "username" to "testuser"
                )
            )
            
            val result1 = withTimeout(5000) { orchestrator.processEvent(registrationEvent) }
            assertTrue(result1.success)
            assertEquals(UserRegistrationExample.VALIDATION_IN_PROGRESS, result1.nextStateId)
            assertEquals(UserRegistrationExample.VALIDATION_IN_PROGRESS, stateManager.getCurrentState()?.id)
            
            // 测试用户信息验证完成事件
            val validationEvent = EventFactory.create(
                type = UserRegistrationExample.USER_INFO_VALIDATED,
                name = "用户信息验证完成",
                description = "用户信息验证通过",
                payload = mapOf("isValid" to true)
            )
            
            val result2 = withTimeout(5000) { orchestrator.processEvent(validationEvent) }
            assertTrue(result2.success)
            assertEquals(UserRegistrationExample.PENDING_VERIFICATION, result2.nextStateId)
            assertEquals(UserRegistrationExample.PENDING_VERIFICATION, stateManager.getCurrentState()?.id)
            
            // 测试用户点击验证链接事件
            val verificationEvent = EventFactory.create(
                type = UserRegistrationExample.VERIFICATION_EMAIL_CLICKED,
                name = "用户点击验证链接",
                description = "用户点击了邮件中的验证链接",
                payload = mapOf("verificationToken" to "token_abc123")
            )
            
            val result3 = withTimeout(5000) { orchestrator.processEvent(verificationEvent) }
            assertTrue(result3.success)
            assertEquals(UserRegistrationExample.VERIFICATION_IN_PROGRESS, result3.nextStateId)
            assertEquals(UserRegistrationExample.VERIFICATION_IN_PROGRESS, stateManager.getCurrentState()?.id)
            
            // 测试注册完成事件
            val completionEvent = EventFactory.create(
                type = UserRegistrationExample.REGISTRATION_COMPLETED,
                name = "注册完成",
                description = "用户注册流程完成",
                payload = mapOf("userId" to "user_12345")
            )
            
            val result4 = withTimeout(5000) { orchestrator.processEvent(completionEvent) }
            assertTrue(result4.success)
            assertEquals(UserRegistrationExample.COMPLETED, result4.nextStateId)
            assertEquals(UserRegistrationExample.COMPLETED, stateManager.getCurrentState()?.id)
            
            // 验证状态历史
            val history = stateManager.getStateHistory()
            assertTrue(history.size >= 4)
            
        } finally {
            // 停止系统
            orchestrator.stop()
        }
    }
    
    @Test
    fun `test user registration failure flow`() = runBlocking {
        // 创建组件
        val eventBus = EventBusFactory.create()
        val stateManager = StateManagerFactory.create()
        val orchestrator = StateOrchestratorFactory.create(stateManager, eventBus)
        
        try {
            // 启动系统
            orchestrator.start()
            
            // 注册状态
            UserRegistrationExample.createStates().forEach { state ->
                stateManager.stateRegistry.registerState(state)
            }
            
            // 注册状态转移
            UserRegistrationExample.createTransitions().forEach { transition ->
                orchestrator.registerTransition(transition)
            }
            
            // 创建初始上下文
            val initialContext = StateContextFactory.create(
                localState = mapOf(
                    "sessionId" to "session_123",
                    "timestamp" to java.time.Instant.now()
                )
            )
            
            // 设置初始状态
            val setStateResult = stateManager.setCurrentState(
                UserRegistrationExample.PENDING_VALIDATION, 
                initialContext
            )
            assertTrue(setStateResult.success)
            
            // 测试用户注册请求事件
            val registrationEvent = EventFactory.create(
                type = UserRegistrationExample.USER_REGISTRATION_REQUESTED,
                name = "用户注册请求",
                description = "用户提交了注册表单",
                payload = mapOf(
                    "email" to "user@example.com",
                    "password" to "password123",
                    "username" to "testuser"
                )
            )
            
            val result1 = withTimeout(5000) { orchestrator.processEvent(registrationEvent) }
            assertTrue(result1.success)
            assertEquals(UserRegistrationExample.VALIDATION_IN_PROGRESS, stateManager.getCurrentState()?.id)
            
            // 测试注册失败事件
            val failureEvent = EventFactory.create(
                type = UserRegistrationExample.REGISTRATION_FAILED,
                name = "注册失败",
                description = "用户注册失败",
                payload = mapOf("reason" to "Invalid email format")
            )
            
            val result2 = withTimeout(5000) { orchestrator.processEvent(failureEvent) }
            assertTrue(result2.success)
            assertEquals(UserRegistrationExample.FAILED, result2.nextStateId)
            assertEquals(UserRegistrationExample.FAILED, stateManager.getCurrentState()?.id)
            
        } finally {
            // 停止系统
            orchestrator.stop()
        }
    }
}
