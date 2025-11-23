package me.ixor.sred.policy

import me.ixor.sred.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * 策略模板接口
 */
interface PolicyTemplate {
    /**
     * 模板ID
     */
    val templateId: String
    
    /**
     * 模板名称
     */
    val templateName: String
    
    /**
     * 模板描述
     */
    val description: String
    
    /**
     * 基础规则（模板定义的默认规则）
     */
    val baseRules: List<PolicyRule>
    
    /**
     * 模板参数（可配置的参数）
     */
    val parameters: Map<String, Any>
    
    /**
     * 基于模板创建策略
     * @param policyId 策略ID
     * @param policyName 策略名称
     * @param overrides 覆盖的规则或参数
     * @return 创建的策略
     */
    fun createPolicy(
        policyId: String,
        policyName: String,
        overrides: PolicyTemplateOverrides = PolicyTemplateOverrides()
    ): TransitionPolicy
}

/**
 * 模板覆盖配置
 */
data class PolicyTemplateOverrides(
    /**
     * 额外的规则
     */
    val additionalRules: List<PolicyRule> = emptyList(),
    
    /**
     * 覆盖的规则（通过规则名称）
     */
    val ruleOverrides: Map<String, PolicyRule> = emptyMap(),
    
    /**
     * 要移除的规则名称列表
     */
    val rulesToRemove: List<String> = emptyList(),
    
    /**
     * 参数覆盖
     */
    val parameterOverrides: Map<String, Any> = emptyMap(),
    
    /**
     * 策略级别覆盖
     */
    val priority: Int? = null,
    val enabled: Boolean? = null,
    val condition: PolicyCondition? = null,
    val effectiveTimeRange: TimeRange? = null
)

/**
 * 策略模板实现
 */
data class PolicyTemplateImpl(
    override val templateId: String,
    override val templateName: String,
    override val description: String,
    override val baseRules: List<PolicyRule>,
    override val parameters: Map<String, Any> = emptyMap()
) : PolicyTemplate {
    
    override fun createPolicy(
        policyId: String,
        policyName: String,
        overrides: PolicyTemplateOverrides
    ): TransitionPolicy {
        // 从基础规则开始
        val rules = baseRules.toMutableList()
        
        // 应用覆盖
        overrides.ruleOverrides.forEach { (ruleName, overrideRule) ->
            val index = rules.indexOfFirst { it.name == ruleName }
            if (index >= 0) {
                rules[index] = overrideRule
            }
        }
        
        // 移除规则
        rules.removeAll { it.name in overrides.rulesToRemove }
        
        // 添加额外规则
        rules.addAll(overrides.additionalRules)
        
        // 合并参数
        val finalParameters = parameters + overrides.parameterOverrides
        
        // 应用策略级别覆盖
        val finalPriority = overrides.priority ?: (parameters["priority"] as? Int) ?: 0
        val finalEnabled = overrides.enabled ?: (parameters["enabled"] as? Boolean) ?: true
        val finalCondition = overrides.condition 
            ?: (parameters["condition"] as? PolicyCondition) 
            ?: PolicyCondition.Always
        val finalTimeRange = overrides.effectiveTimeRange 
            ?: (parameters["effectiveTimeRange"] as? TimeRange)
        
        return TransitionPolicy(
            id = policyId,
            name = policyName,
            description = description,
            version = (parameters["version"] as? String) ?: "1.0",
            priority = finalPriority,
            enabled = finalEnabled,
            rules = rules,
            condition = finalCondition,
            effectiveTimeRange = finalTimeRange,
            metadata = mapOf(
                "templateId" to templateId,
                "createdFromTemplate" to true
            ) + finalParameters
        )
    }
}

/**
 * 策略模板注册表
 */
interface PolicyTemplateRegistry {
    /**
     * 注册模板
     */
    suspend fun registerTemplate(template: PolicyTemplate)
    
    /**
     * 注销模板
     */
    suspend fun unregisterTemplate(templateId: String)
    
    /**
     * 获取模板
     */
    fun getTemplate(templateId: String): PolicyTemplate?
    
    /**
     * 列出所有模板
     */
    fun listTemplates(): List<PolicyTemplate>
    
    /**
     * 基于模板创建策略
     */
    suspend fun createPolicyFromTemplate(
        templateId: String,
        policyId: String,
        policyName: String,
        overrides: PolicyTemplateOverrides = PolicyTemplateOverrides()
    ): TransitionPolicy?
    
    /**
     * 继承模板（创建子模板）
     */
    suspend fun extendTemplate(
        parentTemplateId: String,
        childTemplateId: String,
        childTemplateName: String,
        additionalRules: List<PolicyRule> = emptyList(),
        parameterOverrides: Map<String, Any> = emptyMap()
    ): PolicyTemplate?
}

/**
 * 策略模板注册表实现
 */
class PolicyTemplateRegistryImpl : PolicyTemplateRegistry {
    
    private val templates = mutableMapOf<String, PolicyTemplate>()
    private val mutex = kotlinx.coroutines.sync.Mutex()
    
    override suspend fun registerTemplate(template: PolicyTemplate) {
        mutex.withLock {
            templates[template.templateId] = template
        }
    }
    
    override suspend fun unregisterTemplate(templateId: String) {
        mutex.withLock {
            templates.remove(templateId)
        }
    }
    
    override fun getTemplate(templateId: String): PolicyTemplate? {
        return templates[templateId]
    }
    
    override fun listTemplates(): List<PolicyTemplate> {
        return templates.values.toList()
    }
    
    override suspend fun createPolicyFromTemplate(
        templateId: String,
        policyId: String,
        policyName: String,
        overrides: PolicyTemplateOverrides
    ): TransitionPolicy? {
        return mutex.withLock {
            val template = templates[templateId] ?: return@withLock null
            template.createPolicy(policyId, policyName, overrides)
        }
    }
    
    override suspend fun extendTemplate(
        parentTemplateId: String,
        childTemplateId: String,
        childTemplateName: String,
        additionalRules: List<PolicyRule>,
        parameterOverrides: Map<String, Any>
    ): PolicyTemplate? {
        return mutex.withLock {
            val parentTemplate = templates[parentTemplateId] ?: return@withLock null
            
            // 创建子模板，继承父模板的规则和参数
            val childRules = parentTemplate.baseRules + additionalRules
            val childParameters = parentTemplate.parameters + parameterOverrides
            
            val childTemplate = PolicyTemplateImpl(
                templateId = childTemplateId,
                templateName = childTemplateName,
                description = "Extended from ${parentTemplate.templateName}",
                baseRules = childRules,
                parameters = childParameters
            )
            
            templates[childTemplateId] = childTemplate
            childTemplate
        }
    }
}

/**
 * 策略模板工厂
 */
object PolicyTemplateFactory {
    /**
     * 创建简单模板
     */
    fun createTemplate(
        templateId: String,
        templateName: String,
        description: String,
        baseRules: List<PolicyRule>,
        parameters: Map<String, Any> = emptyMap()
    ): PolicyTemplate {
        return PolicyTemplateImpl(
            templateId = templateId,
            templateName = templateName,
            description = description,
            baseRules = baseRules,
            parameters = parameters
        )
    }
    
    /**
     * 创建默认模板注册表
     */
    fun createRegistry(): PolicyTemplateRegistry = PolicyTemplateRegistryImpl()
}

/**
 * 扩展 PolicyEngine 以支持模板
 */
suspend fun PolicyEngine.createPolicyFromTemplate(
    templateRegistry: PolicyTemplateRegistry,
    templateId: String,
    policyId: String,
    policyName: String,
    overrides: PolicyTemplateOverrides = PolicyTemplateOverrides()
) {
    val policy = templateRegistry.createPolicyFromTemplate(templateId, policyId, policyName, overrides)
        ?: throw IllegalArgumentException("Template not found: $templateId")
    
    registerPolicy(policy)
}

