package me.ixor.sred.api.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 转账请求
 */
data class TransferRequest(
    @JsonProperty("fromUserId")
    val fromUserId: String,
    @JsonProperty("toUserId")
    val toUserId: String,
    @JsonProperty("amount")
    val amount: Double
)

/**
 * 转账响应
 */
data class TransferResponse(
    @JsonProperty("success")
    val success: Boolean,
    @JsonProperty("instanceId")
    val instanceId: String? = null,
    @JsonProperty("finalState")
    val finalState: String? = null,
    @JsonProperty("transferId")
    val transferId: String? = null,
    @JsonProperty("message")
    val message: String? = null
)

