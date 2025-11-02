package me.ixor.sred.api.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 审批请求
 */
data class ApprovalRequest(
    @JsonProperty("applicantId")
    val applicantId: String,
    @JsonProperty("title")
    val title: String,
    @JsonProperty("content")
    val content: String,
    @JsonProperty("amount")
    val amount: Double? = null
)

/**
 * 审批操作请求
 */
data class ApprovalActionRequest(
    @JsonProperty("action")
    val action: String,  // "approve" 或 "reject"
    @JsonProperty("comment")
    val comment: String? = null
)

/**
 * 审批响应
 */
data class ApprovalResponse(
    @JsonProperty("success")
    val success: Boolean,
    @JsonProperty("instanceId")
    val instanceId: String? = null,
    @JsonProperty("currentState")
    val currentState: String? = null,
    @JsonProperty("message")
    val message: String? = null
)

