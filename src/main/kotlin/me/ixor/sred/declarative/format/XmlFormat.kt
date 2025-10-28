package me.ixor.sred.declarative.format

/**
 * XML格式解析器
 * 支持XML格式定义状态流转
 */
object XmlFormat : StateDefinitionFormat {
    
    override fun parse(content: String): StateDefinition {
        // 简化的XML解析实现
        val definition = StateDefinition("", "")
        
        // 这里应该使用真正的XML解析器，如DOM或SAX
        // 为了演示，我们提供一个简化的实现
        val lines = content.lines()
        val states = mutableListOf<StateInfo>()
        val transitions = mutableListOf<TransitionInfo>()
        val functions = mutableListOf<FunctionInfo>()
        var metadata = mutableMapOf<String, Any>()
        
        var currentSection = ""
        var inState = false
        var inTransition = false
        var inFunction = false
        
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("<name>") -> {
                    val name = extractContent(trimmed, "name")
                    return definition.copy(name = name)
                }
                trimmed.startsWith("<description>") -> {
                    val description = extractContent(trimmed, "description")
                    return definition.copy(description = description)
                }
                trimmed.startsWith("<version>") -> {
                    val version = extractContent(trimmed, "version")
                    return definition.copy(version = version)
                }
                trimmed.startsWith("<author>") -> {
                    val author = extractContent(trimmed, "author")
                    return definition.copy(author = author)
                }
                trimmed == "<states>" -> {
                    currentSection = "states"
                }
                trimmed == "</states>" -> {
                    currentSection = ""
                }
                trimmed == "<transitions>" -> {
                    currentSection = "transitions"
                }
                trimmed == "</transitions>" -> {
                    currentSection = ""
                }
                trimmed == "<functions>" -> {
                    currentSection = "functions"
                }
                trimmed == "</functions>" -> {
                    currentSection = ""
                }
                trimmed == "<metadata>" -> {
                    currentSection = "metadata"
                }
                trimmed == "</metadata>" -> {
                    currentSection = ""
                }
                trimmed.startsWith("<state>") -> {
                    inState = true
                }
                trimmed.startsWith("</state>") -> {
                    inState = false
                }
                trimmed.startsWith("<transition>") -> {
                    inTransition = true
                }
                trimmed.startsWith("</transition>") -> {
                    inTransition = false
                }
                trimmed.startsWith("<function>") -> {
                    inFunction = true
                }
                trimmed.startsWith("</function>") -> {
                    inFunction = false
                }
                inState && trimmed.startsWith("<id>") -> {
                    val id = extractContent(trimmed, "id")
                    val state = StateInfo(
                        id = id,
                        name = id.replace("_", " ").replaceFirstChar { it.uppercase() },
                        type = StateType.NORMAL
                    )
                    states.add(state)
                }
                inTransition && trimmed.startsWith("<from>") -> {
                    val from = extractContent(trimmed, "from")
                    val to = extractContent(trimmed, "to")
                    val transition = TransitionInfo(
                        from = from,
                        to = to,
                        condition = TransitionCondition.Success
                    )
                    transitions.add(transition)
                }
                inFunction && trimmed.startsWith("<stateId>") -> {
                    val stateId = extractContent(trimmed, "stateId")
                    val function = FunctionInfo(
                        stateId = stateId,
                        functionName = "handle${stateId.replace("_", "").replaceFirstChar { it.uppercase() }}"
                    )
                    functions.add(function)
                }
            }
        }
        
        return definition.copy(
            states = states,
            transitions = transitions,
            functions = functions,
            metadata = metadata
        )
    }
    
    override fun serialize(definition: StateDefinition): String {
        val sb = StringBuilder()
        
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<stateDefinition>")
        sb.appendLine("    <name>${definition.name}</name>")
        sb.appendLine("    <description>${definition.description}</description>")
        sb.appendLine("    <version>${definition.version}</version>")
        sb.appendLine("    <author>${definition.author}</author>")
        sb.appendLine()
        
        if (definition.states.isNotEmpty()) {
            sb.appendLine("    <states>")
            definition.states.forEach { state ->
                sb.appendLine("        <state>")
                sb.appendLine("            <id>${state.id}</id>")
                sb.appendLine("            <name>${state.name}</name>")
                sb.appendLine("            <type>${state.type.name.lowercase()}</type>")
                if (state.parentId != null) {
                    sb.appendLine("            <parentId>${state.parentId}</parentId>")
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
                    sb.appendLine("            <description>${state.description}</description>")
                }
                sb.appendLine("        </state>")
            }
            sb.appendLine("    </states>")
            sb.appendLine()
        }
        
        if (definition.transitions.isNotEmpty()) {
            sb.appendLine("    <transitions>")
            definition.transitions.forEach { transition ->
                sb.appendLine("        <transition>")
                sb.appendLine("            <from>${transition.from}</from>")
                sb.appendLine("            <to>${transition.to}</to>")
                sb.appendLine("            <condition>${transition.condition}</condition>")
                if (transition.priority > 0) {
                    sb.appendLine("            <priority>${transition.priority}</priority>")
                }
                if (transition.description.isNotEmpty()) {
                    sb.appendLine("            <description>${transition.description}</description>")
                }
                sb.appendLine("        </transition>")
            }
            sb.appendLine("    </transitions>")
            sb.appendLine()
        }
        
        if (definition.functions.isNotEmpty()) {
            sb.appendLine("    <functions>")
            definition.functions.forEach { function ->
                sb.appendLine("        <function>")
                sb.appendLine("            <stateId>${function.stateId}</stateId>")
                sb.appendLine("            <functionName>${function.functionName}</functionName>")
                if (function.className != null) {
                    sb.appendLine("            <className>${function.className}</className>")
                }
                if (function.description.isNotEmpty()) {
                    sb.appendLine("            <description>${function.description}</description>")
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
                    sb.appendLine("            <tags>${function.tags.joinToString(",")}</tags>")
                }
                sb.appendLine("        </function>")
            }
            sb.appendLine("    </functions>")
            sb.appendLine()
        }
        
        if (definition.metadata.isNotEmpty()) {
            sb.appendLine("    <metadata>")
            definition.metadata.forEach { (key, value) ->
                sb.appendLine("        <$key>$value</$key>")
            }
            sb.appendLine("    </metadata>")
        }
        
        sb.appendLine("</stateDefinition>")
        
        return sb.toString()
    }
    
    private fun extractContent(line: String, tag: String): String {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        val startIndex = line.indexOf(startTag) + startTag.length
        val endIndex = line.indexOf(endTag)
        return if (startIndex > startTag.length - 1 && endIndex > startIndex) {
            line.substring(startIndex, endIndex)
        } else {
            ""
        }
    }
}