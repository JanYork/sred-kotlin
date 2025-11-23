package me.ixor.sred.declarative.format

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * XML格式解析器
 * 使用Jackson XML模块解析XML格式的状态定义
 */
object XmlFormat : StateDefinitionFormat {
    
    private val xmlMapper = XmlMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }
    
    override fun parse(content: String): StateDefinition {
        return try {
            // 使用Jackson XML解析
            xmlMapper.readValue<StateDefinition>(content)
        } catch (e: Exception) {
            // 如果Jackson解析失败，回退到简化的解析逻辑
            parseSimple(content)
        }
    }
    
    /**
     * 简化的XML解析实现（作为回退方案）
     * 处理基本的XML结构，但功能有限
     */
    private fun parseSimple(content: String): StateDefinition {
        val states = mutableListOf<StateInfo>()
        val transitions = mutableListOf<TransitionInfo>()
        val functions = mutableListOf<FunctionInfo>()
        val metadata = mutableMapOf<String, Any>()
        
        var name = ""
        var description = ""
        var version = "1.0.0"
        var author = ""
        
        // 解析基本信息
        extractTagContent(content, "name")?.let { name = it }
        extractTagContent(content, "description")?.let { description = it }
        extractTagContent(content, "version")?.let { version = it }
        extractTagContent(content, "author")?.let { author = it }
        
        // 解析状态列表
        val statesSection = extractSection(content, "states")
        if (statesSection != null) {
            val stateMatches = Regex("<state>.*?</state>", RegexOption.DOT_MATCHES_ALL).findAll(statesSection)
            stateMatches.forEach { match ->
                val stateXml = match.value
                val id = extractTagContent(stateXml, "id") ?: ""
                val stateName = extractTagContent(stateXml, "name") ?: id.replace("_", " ").replaceFirstChar { it.uppercase() }
                val typeStr = extractTagContent(stateXml, "type")?.uppercase() ?: "NORMAL"
                val type = try {
                    StateType.valueOf(typeStr)
                } catch (e: Exception) {
                    StateType.NORMAL
                }
                val parentId = extractTagContent(stateXml, "parentId")
                val isInitial = extractTagContent(stateXml, "isInitial")?.toBoolean() ?: false
                val isFinal = extractTagContent(stateXml, "isFinal")?.toBoolean() ?: false
                val isError = extractTagContent(stateXml, "isError")?.toBoolean() ?: false
                val stateDescription = extractTagContent(stateXml, "description") ?: ""
                
                if (id.isNotEmpty()) {
                    states.add(StateInfo(
                        id = id,
                        name = stateName,
                        type = type,
                        parentId = parentId,
                        isInitial = isInitial,
                        isFinal = isFinal,
                        isError = isError,
                        description = stateDescription
                    ))
                }
            }
        }
        
        // 解析转移列表
        val transitionsSection = extractSection(content, "transitions")
        if (transitionsSection != null) {
            val transitionMatches = Regex("<transition>.*?</transition>", RegexOption.DOT_MATCHES_ALL).findAll(transitionsSection)
            transitionMatches.forEach { match ->
                val transitionXml = match.value
                val from = extractTagContent(transitionXml, "from") ?: ""
                val to = extractTagContent(transitionXml, "to") ?: ""
                val conditionStr = extractTagContent(transitionXml, "condition") ?: "Success"
                val condition = when (conditionStr.uppercase()) {
                    "SUCCESS" -> TransitionCondition.Success
                    "FAILURE" -> TransitionCondition.Failure
                    else -> TransitionCondition.Custom(conditionStr)
                }
                val priority = extractTagContent(transitionXml, "priority")?.toIntOrNull() ?: 0
                val transitionDescription = extractTagContent(transitionXml, "description") ?: ""
                
                if (from.isNotEmpty() && to.isNotEmpty()) {
                    transitions.add(TransitionInfo(
                        from = from,
                        to = to,
                        condition = condition,
                        priority = priority,
                        description = transitionDescription
                    ))
                }
            }
        }
        
        // 解析函数列表
        val functionsSection = extractSection(content, "functions")
        if (functionsSection != null) {
            val functionMatches = Regex("<function>.*?</function>", RegexOption.DOT_MATCHES_ALL).findAll(functionsSection)
            functionMatches.forEach { match ->
                val functionXml = match.value
                val stateId = extractTagContent(functionXml, "stateId") ?: ""
                val functionName = extractTagContent(functionXml, "functionName")
                    ?: "handle${stateId.replace("_", "").replaceFirstChar { it.uppercase() }}"
                val className = extractTagContent(functionXml, "className")
                val functionDescription = extractTagContent(functionXml, "description") ?: ""
                val priority = extractTagContent(functionXml, "priority")?.toIntOrNull() ?: 0
                val timeout = extractTagContent(functionXml, "timeout")?.toLongOrNull() ?: 0L
                val retryCount = extractTagContent(functionXml, "retryCount")?.toIntOrNull() ?: 0
                val async = extractTagContent(functionXml, "async")?.toBoolean() ?: false
                
                if (stateId.isNotEmpty()) {
                    functions.add(FunctionInfo(
                        stateId = stateId,
                        functionName = functionName,
                        className = className,
                        description = functionDescription,
                        priority = priority,
                        timeout = timeout,
                        retryCount = retryCount,
                        async = async
                    ))
                }
            }
        }
        
        return StateDefinition(
            name = name,
            description = description,
            version = version,
            author = author,
            states = states,
            transitions = transitions,
            functions = functions,
            metadata = metadata
        )
    }
    
    /**
     * 提取XML标签内容
     */
    private fun extractTagContent(xml: String, tag: String): String? {
        val pattern = Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
    
    /**
     * 提取XML段落（section）
     */
    private fun extractSection(xml: String, sectionName: String): String? {
        val pattern = Regex("<$sectionName>(.*?)</$sectionName>", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
    
    override fun serialize(definition: StateDefinition): String {
        return try {
            // 使用Jackson XML序列化
            xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(definition)
        } catch (e: Exception) {
            // 如果Jackson序列化失败，使用手动构建
            serializeSimple(definition)
        }
    }
    
    /**
     * 简化的XML序列化实现（作为回退方案）
     */
    private fun serializeSimple(definition: StateDefinition): String {
        val sb = StringBuilder()
        
        // 转义XML特殊字符
        fun escapeXml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }
        
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<stateDefinition>")
        sb.appendLine("    <name>${escapeXml(definition.name)}</name>")
        if (definition.description.isNotEmpty()) {
            sb.appendLine("    <description>${escapeXml(definition.description)}</description>")
        }
        sb.appendLine("    <version>${escapeXml(definition.version)}</version>")
        if (definition.author.isNotEmpty()) {
            sb.appendLine("    <author>${escapeXml(definition.author)}</author>")
        }
        
        if (definition.states.isNotEmpty()) {
            sb.appendLine("    <states>")
            definition.states.forEach { state ->
                sb.appendLine("        <state>")
                sb.appendLine("            <id>${escapeXml(state.id)}</id>")
                sb.appendLine("            <name>${escapeXml(state.name)}</name>")
                sb.appendLine("            <type>${state.type.name.lowercase()}</type>")
                if (state.parentId != null) {
                    sb.appendLine("            <parentId>${escapeXml(state.parentId)}</parentId>")
                }
                if (state.isInitial) {
                    sb.appendLine("            <isInitial>true</isInitial>")
                }
                if (state.isFinal) {
                    sb.appendLine("            <isFinal>true</isFinal>")
                }
                if (state.isError) {
                    sb.appendLine("            <isError>true</isError>")
                }
                if (state.description.isNotEmpty()) {
                    sb.appendLine("            <description>${escapeXml(state.description)}</description>")
                }
                sb.appendLine("        </state>")
            }
            sb.appendLine("    </states>")
        }
        
        if (definition.transitions.isNotEmpty()) {
            sb.appendLine("    <transitions>")
            definition.transitions.forEach { transition ->
                sb.appendLine("        <transition>")
                sb.appendLine("            <from>${escapeXml(transition.from)}</from>")
                sb.appendLine("            <to>${escapeXml(transition.to)}</to>")
                val conditionStr = when (transition.condition) {
                    is TransitionCondition.Success -> "Success"
                    is TransitionCondition.Failure -> "Failure"
                    is TransitionCondition.Custom -> transition.condition.predicate
                }
                sb.appendLine("            <condition>${escapeXml(conditionStr)}</condition>")
                if (transition.priority > 0) {
                    sb.appendLine("            <priority>${transition.priority}</priority>")
                }
                if (transition.description.isNotEmpty()) {
                    sb.appendLine("            <description>${escapeXml(transition.description)}</description>")
                }
                sb.appendLine("        </transition>")
            }
            sb.appendLine("    </transitions>")
        }
        
        if (definition.functions.isNotEmpty()) {
            sb.appendLine("    <functions>")
            definition.functions.forEach { function ->
                sb.appendLine("        <function>")
                sb.appendLine("            <stateId>${escapeXml(function.stateId)}</stateId>")
                sb.appendLine("            <functionName>${escapeXml(function.functionName)}</functionName>")
                if (function.className != null) {
                    sb.appendLine("            <className>${escapeXml(function.className)}</className>")
                }
                if (function.description.isNotEmpty()) {
                    sb.appendLine("            <description>${escapeXml(function.description)}</description>")
                }
                if (function.priority > 0) {
                    sb.appendLine("            <priority>${function.priority}</priority>")
                }
                if (function.timeout > 0) {
                    sb.appendLine("            <timeout>${function.timeout}</timeout>")
                }
                if (function.retryCount > 0) {
                    sb.appendLine("            <retryCount>${function.retryCount}</retryCount>")
                }
                if (function.async) {
                    sb.appendLine("            <async>true</async>")
                }
                if (function.tags.isNotEmpty()) {
                    sb.appendLine("            <tags>${escapeXml(function.tags.joinToString(","))}</tags>")
                }
                sb.appendLine("        </function>")
            }
            sb.appendLine("    </functions>")
        }
        
        if (definition.metadata.isNotEmpty()) {
            sb.appendLine("    <metadata>")
            definition.metadata.forEach { (key, value) ->
                sb.appendLine("        <${escapeXml(key)}>${escapeXml(value.toString())}</${escapeXml(key)}>")
            }
            sb.appendLine("    </metadata>")
        }
        
        sb.appendLine("</stateDefinition>")
        
        return sb.toString()
    }
}