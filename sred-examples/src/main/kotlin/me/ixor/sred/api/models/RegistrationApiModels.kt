package me.ixor.sred.api.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 用户注册请求
 */
data class RegistrationRequest(
    @JsonProperty("username")
    val username: String,
    @JsonProperty("email")
    val email: String,
    @JsonProperty("password")
    val password: String,
    @JsonProperty("verificationCode")
    val verificationCode: String? = null
)

/**
 * 用户注册响应
 */
data class RegistrationResponse(
    @JsonProperty("success")
    val success: Boolean,
    @JsonProperty("instanceId")
    val instanceId: String? = null,
    @JsonProperty("finalState")
    val finalState: String? = null,
    @JsonProperty("userId")
    val userId: String? = null,
    @JsonProperty("message")
    val message: String? = null
)

