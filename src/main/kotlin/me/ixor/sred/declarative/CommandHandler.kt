package me.ixor.sred.declarative

import me.ixor.sred.core.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 命令处理器
 */
class CommandHandler {
    
    private val stateMachines = ConcurrentHashMap<String, StateMachine>()
    private val instances = ConcurrentHashMap<String, StateMachineInstance>()
    
    /**
     * 注册状态机
     */
    fun registerStateMachine(name: String, stateMachine: StateMachine) {
        stateMachines[name] = stateMachine
    }
    
    /**
     * 处理命令
     */
    suspend fun handleCommand(command: Command): CommandResult {
        val stateMachine = stateMachines[command.type] 
            ?: return CommandResult(false, error = IllegalArgumentException("Unknown command type: ${command.type}"))
        
        try {
            // 创建状态机实例
            val instanceId = "${command.type}_${System.currentTimeMillis()}"
            val instance = stateMachine.start(instanceId, command.context)
            instances[instanceId] = instance
            
            // 处理命令
            val result = processCommand(instance, command)
            
            return CommandResult(true, result.data, result.error)
            
        } catch (e: Exception) {
            return CommandResult(false, error = e)
        }
    }
    
    /**
     * 处理命令的具体逻辑
     */
    private suspend fun processCommand(instance: StateMachineInstance, command: Command): StateResult {
        // 创建开始事件
        val startEvent = EventFactory.create(
            type = EventType("command", command.type),
            name = "开始处理命令",
            description = "开始处理命令: ${command.type}",
            payload = command.data
        )
        
        // 处理事件，触发状态流转
        val result = instance.processEvent(startEvent)
        
        // 如果当前状态是最终状态，返回结果
        val currentState = instance.getCurrentState()
        if (currentState != null && isFinalState(currentState)) {
            return result
        }
        
        return result
    }
    
    /**
     * 检查是否为最终状态
     */
    private fun isFinalState(stateId: String): Boolean {
        return stateId.endsWith("_completed") || 
               stateId.endsWith("_sent") || 
               stateId.contains("_failed")
    }
    
    /**
     * 获取实例状态
     */
    fun getInstanceStatus(instanceId: String): String? {
        return instances[instanceId]?.getCurrentState()
    }
    
    /**
     * 清理实例
     */
    fun cleanupInstance(instanceId: String) {
        instances.remove(instanceId)
    }
}

/**
 * 命令定义
 */
data class Command(
    val type: String,
    val data: Map<String, Any> = emptyMap(),
    val context: StateContext = StateContextFactory.create()
)

/**
 * 命令结果
 */
data class CommandResult(
    val success: Boolean,
    val data: Map<String, Any> = emptyMap(),
    val error: Throwable? = null
)

/**
 * 用户注册命令
 */
data class UserRegistrationCommand(
    val email: String,
    val password: String,
    val username: String
) {
    fun toCommand(): Command {
        return Command(
            type = "user_registration",
            data = mapOf(
                "email" to email,
                "password" to password,
                "username" to username
            ),
            context = StateContextFactory.create(
                localState = mapOf(
                    "email" to email,
                    "password" to password,
                    "username" to username,
                    "timestamp" to java.time.Instant.now()
                )
            )
        )
    }
}
