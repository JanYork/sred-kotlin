package me.ixor.sred.api.controllers

import me.ixor.sred.api.models.RegistrationRequest
import me.ixor.sred.api.models.RegistrationResponse
import me.ixor.sred.api.models.TransferRequest
import me.ixor.sred.api.models.TransferResponse
import me.ixor.sred.api.services.RegistrationApiService
import me.ixor.sred.api.services.TransferApiService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import kotlinx.coroutines.runBlocking

/**
 * API 控制器
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = ["*"])
class ApiController(
    private val transferService: TransferApiService,
    private val registrationService: RegistrationApiService
) {
    
    /**
     * POST /api/v1/transfer
     * 执行转账
     */
    @PostMapping("/transfer")
    fun executeTransfer(@RequestBody request: TransferRequest): ResponseEntity<TransferResponse> {
        return runBlocking {
            try {
                val response = transferService.executeTransfer(request)
                if (response.success) {
                    ResponseEntity.ok(response)
                } else {
                    ResponseEntity.badRequest().body(response)
                }
            } catch (e: Exception) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TransferResponse(success = false, message = e.message ?: "服务器内部错误"))
            }
        }
    }
    
    /**
     * GET /api/v1/transfer/{instanceId}
     * 查询转账状态
     */
    @GetMapping("/transfer/{instanceId}")
    fun getTransferStatus(@PathVariable instanceId: String): ResponseEntity<TransferResponse> {
        return runBlocking {
            try {
                val response = transferService.getTransferStatus(instanceId)
                if (response.instanceId != null) {
                    ResponseEntity.ok(response)
                } else {
                    ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
                }
            } catch (e: Exception) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TransferResponse(success = false, message = e.message ?: "服务器内部错误"))
            }
        }
    }
    
    /**
     * POST /api/v1/registration
     * 执行用户注册
     */
    @PostMapping("/registration")
    fun executeRegistration(@RequestBody request: RegistrationRequest): ResponseEntity<RegistrationResponse> {
        return runBlocking {
            try {
                val response = registrationService.executeRegistration(request)
                if (response.success) {
                    ResponseEntity.ok(response)
                } else {
                    ResponseEntity.badRequest().body(response)
                }
            } catch (e: Exception) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RegistrationResponse(success = false, message = e.message ?: "服务器内部错误"))
            }
        }
    }
    
    /**
     * GET /api/v1/registration/{instanceId}
     * 查询注册状态
     */
    @GetMapping("/registration/{instanceId}")
    fun getRegistrationStatus(@PathVariable instanceId: String): ResponseEntity<RegistrationResponse> {
        return runBlocking {
            try {
                val response = registrationService.getRegistrationStatus(instanceId)
                if (response.instanceId != null) {
                    ResponseEntity.ok(response)
                } else {
                    ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
                }
            } catch (e: Exception) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RegistrationResponse(success = false, message = e.message ?: "服务器内部错误"))
            }
        }
    }
    
    /**
     * POST /api/v1/registration/{instanceId}/verify
     * 提交验证码，继续注册流程
     */
    @PostMapping("/registration/{instanceId}/verify")
    fun submitVerificationCode(
        @PathVariable instanceId: String,
        @RequestBody request: Map<String, String>
    ): ResponseEntity<RegistrationResponse> {
        return runBlocking {
            try {
                val verificationCode = request["verificationCode"]
                    ?: throw IllegalArgumentException("verificationCode 参数缺失")
                
                val response = registrationService.submitVerificationCode(instanceId, verificationCode)
                
                if (response.success) {
                    ResponseEntity.ok(response)
                } else {
                    ResponseEntity.badRequest().body(response)
                }
            } catch (e: Exception) {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RegistrationResponse(success = false, message = e.message ?: "服务器内部错误"))
            }
        }
    }
    
    /**
     * GET /health
     * 健康检查
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "ok"))
    }
}

