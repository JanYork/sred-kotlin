package me.ixor.sred.api.services

import me.ixor.sred.*
import me.ixor.sred.core.getMetadata
import me.ixor.sred.core.getLocalState
import me.ixor.sred.api.models.RegistrationRequest
import me.ixor.sred.api.models.RegistrationResponse
import me.ixor.sred.examples.registration.*
import me.ixor.sred.core.logger
import kotlinx.coroutines.*
import org.springframework.stereotype.Service

/**
 * 用户注册 API 服务
 * 支持长时间停顿的异步工作流
 */
@Service
class RegistrationApiService {
    private val log = logger<RegistrationApiService>()
    private val context = RegistrationFixtures.createContext()
    private val service = RegistrationService(context.users, context.verificationCodes)
    private var engine: SREDEngine? = null
    private val executor = WorkflowExecutor()
    
    init {
        runBlocking {
            engine = SRED.fromConfig(
                configPath = "registration.json",
                dbPath = "registration_state.db",
                handlers = service
            )
            // 注册引擎到执行器，用于超时检查
            executor.registerEngine("registration", engine!!, instanceIdPrefix = "registration_")
            
            // 启动时恢复暂停的实例
            executor.restorePausedInstances(engine!!)
        }
    }
    
    /**
     * 启动用户注册流程（异步执行，立即返回）
     * 流程会在等待验证状态自动暂停
     */
    suspend fun executeRegistration(request: RegistrationRequest): RegistrationResponse {
        val engine = this.engine ?: throw IllegalStateException("引擎未初始化")
        
        return try {
            val instanceId = "registration_${System.currentTimeMillis()}"
            
            // 启动工作流实例
            engine.start(instanceId, mapOf(
                "username" to request.username,
                "email" to request.email,
                "password" to request.password
            ))
            
            // 异步执行，根据配置自动在 pauseOnEnter 状态暂停
            // 配置中 waiting_verification 状态设置了 pauseOnEnter=true，会自动暂停
            executor.executeAsync(
                engine = engine,
                instanceId = instanceId,
                autoProcess = false,  // 不自动处理到最后，根据配置决定暂停
                stopStates = null,    // 使用配置中的 pauseOnEnter，不硬编码
                onStateChange = { from, to ->
                    log.info { "注册流程状态: ${from ?: "初始"} -> $to" }
                    // 开发期辅助日志：当进入发送邮件或等待验证时，输出验证码，便于本地联调
                    if (to == RegistrationStates.SENDING_EMAIL || to == RegistrationStates.WAITING_VERIFICATION) {
                        try {
                            runBlocking {
                                val ctx = engine.getContext(instanceId)
                                val email: String? = ctx?.getLocalState("email")
                                val codeFromState: String? = ctx?.getLocalState("verificationCode")
                                val code: String? = codeFromState ?: email?.let { e -> context.verificationCodes[e] }
                                if (email != null && code != null) {
                                    // 与 RegistrationView 的格式保持一致，方便 grep
                                    log.info { "验证码已发送到 $email: $code" }
                                }
                            }
                        } catch (_: Exception) {
                            // 忽略开发期辅助日志的异常
                        }
                    }
                },
                onComplete = { state ->
                    log.info { "注册流程完成: $state" }
                }
            )
            
            // 等待一小段时间，确保流程执行到暂停状态
            delay(500)
            
            // 立即返回当前状态（支持从持久化存储查询）
            val currentState = engine.getCurrentState(instanceId)
            
            RegistrationResponse(
                success = false,  // 初始状态，还未完成
                instanceId = instanceId,
                finalState = currentState,
                message = "注册流程已启动，等待验证邮件"
            )
        } catch (e: Exception) {
            log.error(e) { "启动注册流程失败" }
            RegistrationResponse(
                success = false,
                message = e.message ?: "启动注册流程失败"
            )
        }
    }
    
    /**
     * 提交验证码，继续注册流程
     */
    suspend fun submitVerificationCode(instanceId: String, verificationCode: String): RegistrationResponse {
        val engine = this.engine ?: throw IllegalStateException("引擎未初始化")
        
        return try {
            val currentState = engine.getCurrentState(instanceId)
            
            if (currentState != RegistrationStates.WAITING_VERIFICATION) {
                return RegistrationResponse(
                    success = false,
                    instanceId = instanceId,
                    finalState = currentState,
                    message = "当前状态不允许提交验证码，状态: $currentState"
                )
            }
            
            // 在触发事件前，先把验证码写入上下文的局部状态，供下一个状态函数读取
            runBlocking {
                val ctx = engine.getContext(instanceId)
                if (ctx != null) {
                    val updated = ctx.updateLocalState("inputCode", verificationCode)
                    engine.getPersistence()?.saveContext(updated)
                }
            }
            
                   // 触发验证码验证事件
                   val result = executor.triggerEvent(
                       engine = engine,
                       instanceId = instanceId,
                       eventType = "verify",
                       eventName = "验证验证码",
                       payload = mapOf("inputCode" to verificationCode)
                   )
                   
                   // 如果状态转移，从暂停列表中移除
                   if (result.success) {
                       val newState = engine.getCurrentState(instanceId)
                       val context = engine.getContext(instanceId)
                       val oldState = context?.getMetadata<String>("_pausedState")
                       if (newState != oldState && oldState != null) {
                           // 状态已转移，移除暂停标记
                           executor.removePausedInstance(instanceId)
                       }
                       
                       // 继续执行后续流程
                       executor.continueExecution(
                           engine = engine,
                           instanceId = instanceId,
                           onStateChange = { from, to ->
                               log.info { "注册流程状态: ${from ?: "初始"} -> $to" }
                           },
                           onComplete = { state ->
                               log.info { "注册流程完成: $state" }
                           }
                       )
                   }
            
            val finalState = engine.getCurrentState(instanceId)
            val userId: String? = engine.getDataTyped<String>(instanceId, "userId")
            
            RegistrationResponse(
                success = finalState?.contains("success") == true,
                instanceId = instanceId,
                finalState = finalState,
                userId = userId,
                message = if (result.success) {
                    if (finalState?.contains("success") == true) "验证成功，注册完成" else "验证成功，正在处理"
                } else {
                    "验证失败: ${result.error?.message ?: "验证码错误"}"
                }
            )
        } catch (e: Exception) {
            log.error(e) { "提交验证码失败" }
            RegistrationResponse(
                success = false,
                instanceId = instanceId,
                message = e.message ?: "提交验证码失败"
            )
        }
    }
    
    /**
     * 获取注册状态（支持从持久化存储查询，即使实例不在内存中）
     */
    suspend fun getRegistrationStatus(instanceId: String): RegistrationResponse {
        val engine = this.engine ?: throw IllegalStateException("引擎未初始化")
        
        return try {
            // getCurrentState 和 getContext 会自动从持久化存储加载
            val currentState = engine.getCurrentState(instanceId)
            val context = engine.getContext(instanceId)
            val userId: String? = context?.let { ctx ->
                ctx.getLocalState<String>("userId") ?: engine.getDataTyped<String>(instanceId, "userId")
            } ?: engine.getDataTyped<String>(instanceId, "userId")
            
            RegistrationResponse(
                success = currentState?.contains("success") == true,
                instanceId = instanceId,
                finalState = currentState,
                userId = userId,
                message = when {
                    currentState == null -> "实例不存在"
                    currentState.contains("success") -> "注册成功"
                    currentState.contains("failed") -> "注册失败"
                    currentState == RegistrationStates.WAITING_VERIFICATION -> "等待验证，请提交验证码"
                    else -> "处理中"
                }
            )
        } catch (e: Exception) {
            log.error(e) { "查询状态失败" }
            RegistrationResponse(
                success = false,
                message = e.message ?: "查询失败"
            )
        }
    }
    
    /**
     * 获取所有暂停的实例
     */
    fun getPausedInstances(): List<WorkflowExecutor.PausedInstanceInfo> {
        return executor.getPausedInstances()
    }
    
    
    /**
     * 关闭服务
     */
    suspend fun close() {
        executor.close()
        engine?.close()
    }
}

