package me.ixor.sred.policy

import me.ixor.sred.core.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.*
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.net.URL
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 策略加载器 - 支持从多种来源加载策略
 * 
 * 符合论文要求："系统策略可在运行时实时变更，无需重构"
 */
object PolicyLoader {
    private val jsonMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())
    
    /**
     * 从YAML文件加载策略
     */
    suspend fun fromYaml(filePath: String): List<TransitionPolicy> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Policy file not found: $filePath")
        }
        val content = file.readText()
        fromYamlString(content)
    }
    
    /**
     * 从YAML字符串加载策略
     */
    fun fromYamlString(content: String): List<TransitionPolicy> {
        return try {
            val policyDefinition = yamlMapper.readValue<PolicyDefinition>(content)
            policyDefinition.policies.map { it.toTransitionPolicy() }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse YAML policy: ${e.message}", e)
        }
    }
    
    /**
     * 从JSON文件加载策略
     */
    suspend fun fromJson(filePath: String): List<TransitionPolicy> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Policy file not found: $filePath")
        }
        val content = file.readText()
        fromJsonString(content)
    }
    
    /**
     * 从JSON字符串加载策略
     */
    fun fromJsonString(content: String): List<TransitionPolicy> {
        return try {
            val policyDefinition = jsonMapper.readValue<PolicyDefinition>(content)
            policyDefinition.policies.map { it.toTransitionPolicy() }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse JSON policy: ${e.message}", e)
        }
    }
    
    /**
     * 从数据库加载策略
     */
    suspend fun fromDatabase(
        connection: Any,  // 可以是 JDBC Connection 或其他数据库连接
        tableName: String = "policies"
    ): List<TransitionPolicy> = withContext(Dispatchers.IO) {
        // 这是一个接口，具体实现依赖于数据库类型
        // 可以通过扩展函数为不同类型的数据库提供实现
        throw UnsupportedOperationException("Database loading requires specific implementation")
    }
    
    /**
     * 从远程服务加载策略
     */
    suspend fun fromRemoteService(
        serviceUrl: String,
        headers: Map<String, String> = emptyMap(),
        timeout: Long = 5000
    ): List<TransitionPolicy> = withContext(Dispatchers.IO) {
        try {
            val url = URL(serviceUrl)
            val connection = url.openConnection()
            connection.connectTimeout = timeout.toInt()
            connection.readTimeout = timeout.toInt()
            
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            
            connection.connect()
            val content = connection.getInputStream().bufferedReader().use { it.readText() }
            
            // 根据Content-Type判断格式
            val contentType = connection.contentType?.lowercase() ?: ""
            when {
                contentType.contains("json") -> fromJsonString(content)
                contentType.contains("yaml") || contentType.contains("yml") -> fromYamlString(content)
                else -> {
                    // 尝试JSON
                    try {
                        fromJsonString(content)
                    } catch (e: Exception) {
                        fromYamlString(content)
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to load policies from remote service: ${e.message}", e)
        }
    }
    
    /**
     * 策略定义（用于序列化/反序列化）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PolicyDefinition(
        @JsonProperty("policies")
        val policies: List<PolicyDefinitionItem> = emptyList(),
        
        @JsonProperty("metadata")
        val metadata: Map<String, Any> = emptyMap()
    )
    
    /**
     * 单个策略定义项
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PolicyDefinitionItem(
        @JsonProperty("id")
        val id: String,
        
        @JsonProperty("name")
        val name: String,
        
        @JsonProperty("description")
        val description: String = "",
        
        @JsonProperty("version")
        val version: String = "1.0",
        
        @JsonProperty("priority")
        val priority: Int = 0,
        
        @JsonProperty("enabled")
        val enabled: Boolean = true,
        
        @JsonProperty("rules")
        val rules: List<RuleDefinition> = emptyList(),
        
        @JsonProperty("condition")
        val condition: ConditionDefinition? = null,
        
        @JsonProperty("effectiveTimeRange")
        val effectiveTimeRange: TimeRangeDefinition? = null,
        
        @JsonProperty("metadata")
        val metadata: Map<String, Any> = emptyMap()
    ) {
        fun toTransitionPolicy(): TransitionPolicy {
            return TransitionPolicy(
                id = id,
                name = name,
                description = description,
                version = version,
                priority = priority,
                enabled = enabled,
                rules = rules.map { it.toPolicyRule() },
                condition = condition?.toPolicyCondition() ?: PolicyCondition.Always,
                effectiveTimeRange = effectiveTimeRange?.toTimeRange(),
                metadata = metadata
            )
        }
    }
    
    /**
     * 规则定义
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class RuleDefinition(
        @JsonProperty("name")
        val name: String,
        
        @JsonProperty("type")
        val type: String,  // ALLOW, DENY, RECOMMEND, etc.
        
        @JsonProperty("condition")
        val condition: ConditionDefinition? = null,
        
        @JsonProperty("action")
        val action: ActionDefinition? = null,
        
        @JsonProperty("weight")
        val weight: Double = 1.0
    ) {
        fun toPolicyRule(): PolicyRule {
            val ruleType = RuleType.valueOf(type.uppercase())
            val ruleCondition = condition?.toRuleCondition() ?: RuleCondition.Always
            val ruleAction = action?.toRuleAction() ?: RuleAction.SetComplianceScore(0.5)
            
            return PolicyRule(
                name = name,
                type = ruleType,
                condition = ruleCondition,
                action = ruleAction,
                weight = weight
            )
        }
    }
    
    /**
     * 条件定义
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    sealed class ConditionDefinition {
        abstract fun toRuleCondition(): RuleCondition
        abstract fun toPolicyCondition(): PolicyCondition
        
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class Always(
            @JsonProperty("type")
            val type: String = "always"
        ) : ConditionDefinition() {
            override fun toRuleCondition() = RuleCondition.Always
            override fun toPolicyCondition() = PolicyCondition.Always
        }
        
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class StateBased(
            @JsonProperty("type")
            val type: String = "state",
            
            @JsonProperty("fromStates")
            val fromStates: List<String>? = null,
            
            @JsonProperty("toStates")
            val toStates: List<String>? = null
        ) : ConditionDefinition() {
            override fun toRuleCondition() = RuleCondition.StateBased(
                fromStates = fromStates?.toSet(),
                toStates = toStates?.toSet()
            )
            override fun toPolicyCondition() = PolicyCondition.Always  // State condition only for rules
        }
        
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class EventBased(
            @JsonProperty("type")
            val type: String = "event",
            
            @JsonProperty("eventTypes")
            val eventTypes: List<String>? = null
        ) : ConditionDefinition() {
            override fun toRuleCondition() = RuleCondition.EventBased(
                eventTypes = eventTypes?.toSet()
            )
            override fun toPolicyCondition() = PolicyCondition.Always
        }
        
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class ContextBased(
            @JsonProperty("type")
            val type: String = "context",
            
            @JsonProperty("expression")
            val expression: String  // 简化版：表达式字符串（实际可以更复杂）
        ) : ConditionDefinition() {
            override fun toRuleCondition(): RuleCondition {
                // 这里需要一个表达式解析器，简化实现
                return RuleCondition.ContextBased { context ->
                    // 简化实现：检查局部状态中是否存在表达式指定的键值对
                    // 实际应该使用表达式引擎
                    try {
                        val parts = expression.split("==")
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim().trim('"').trim('\'')
                            context.localState[key]?.toString() == value
                        } else {
                            true
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            override fun toPolicyCondition(): PolicyCondition {
                // PolicyCondition 不支持 ContextBased，使用 Always
                // 实际上下文条件在 RuleCondition 中处理
                return PolicyCondition.Always
            }
        }
    }
    
    /**
     * 动作定义
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ActionDefinition(
        @JsonProperty("type")
        val type: String,
        
        @JsonProperty("score")
        val score: Double? = null,
        
        @JsonProperty("delta")
        val delta: Double? = null,
        
        @JsonProperty("priority")
        val priority: Int? = null,
        
        @JsonProperty("priorityDelta")
        val priorityDelta: Int? = null
    ) {
        fun toRuleAction(): RuleAction {
            return when (type.uppercase()) {
                "SET_COMPLIANCE_SCORE" -> RuleAction.SetComplianceScore(score ?: 0.5)
                "ADJUST_COMPLIANCE_SCORE" -> RuleAction.AdjustComplianceScore(delta ?: 0.0)
                "SET_PRIORITY" -> RuleAction.SetPriority(priority ?: 0)
                "ADJUST_PRIORITY" -> RuleAction.AdjustPriority(priorityDelta ?: 0)
                else -> RuleAction.SetComplianceScore(0.5)
            }
        }
    }
    
    /**
     * 时间范围定义
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TimeRangeDefinition(
        @JsonProperty("start")
        val start: String? = null,  // ISO 8601格式
        
        @JsonProperty("end")
        val end: String? = null
    ) {
        fun toTimeRange(): TimeRange? {
            if (start == null && end == null) return null
            
            val startInstant = start?.let { Instant.parse(it) }
            val endInstant = end?.let { Instant.parse(it) }
            
            if (startInstant == null && endInstant == null) return null
            
            return TimeRange(
                start = startInstant ?: Instant.MIN,
                end = endInstant ?: Instant.MAX
            )
        }
    }
}

/**
 * 策略文件监听器 - 支持热重载
 */
class PolicyWatcher(
    private val filePath: String,
    private val policyEngine: PolicyEngine,
    private val reloadOnChange: Boolean = true
) {
    private var watchJob: Job? = null
    private var lastModified: Long = 0
    
    /**
     * 开始监听文件变化
     */
    suspend fun start(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        stop()
        
        watchJob = scope.launch {
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("Policy file not found: $filePath")
            }
            
            lastModified = file.lastModified()
            
            while (isActive) {
                delay(1000)  // 每秒检查一次
                
                if (file.exists() && file.lastModified() > lastModified) {
                    lastModified = file.lastModified()
                    
                    if (reloadOnChange) {
                        try {
                            reload()
                        } catch (e: Exception) {
                            // 记录错误但不中断监听
                            println("Failed to reload policy file: ${e.message}")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 停止监听
     */
    fun stop() {
        watchJob?.cancel()
        watchJob = null
    }
    
    /**
     * 手动重载策略
     */
    suspend fun reload() = withContext(Dispatchers.IO) {
        val policies = when {
            filePath.endsWith(".yaml") || filePath.endsWith(".yml") -> {
                PolicyLoader.fromYaml(filePath)
            }
            filePath.endsWith(".json") -> {
                PolicyLoader.fromJson(filePath)
            }
            else -> {
                throw IllegalArgumentException("Unsupported file format: $filePath")
            }
        }
        
        // 重新注册所有策略
        policies.forEach { policy ->
            if (policyEngine.getApplicablePolicies(StateContextFactory.create()).any { it.id == policy.id }) {
                policyEngine.updatePolicy(policy.id, policy)
            } else {
                policyEngine.registerPolicy(policy)
            }
        }
    }
}

