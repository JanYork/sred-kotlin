package me.ixor.sred.declarative.format

// 移除序列化依赖

/**
 * DSL格式解析器
 * 支持类似YAML的DSL语法定义状态流转
 */
object DslFormat : StateDefinitionFormat {
    
    override fun parse(content: String): StateDefinition {
        val lines = content.lines().filter { it.trim().isNotEmpty() }
        val definition = StateDefinition("", "")
        
        var currentSection = ""
        val states = mutableListOf<StateInfo>()
        val transitions = mutableListOf<TransitionInfo>()
        val functions = mutableListOf<FunctionInfo>()
        var metadata = mutableMapOf<String, Any>()
        
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#") -> continue // 注释
                trimmed.startsWith("name:") -> {
                    val name = trimmed.substringAfter(":").trim()
                    return definition.copy(name = name)
                }
                trimmed.startsWith("description:") -> {
                    val description = trimmed.substringAfter(":").trim()
                    return definition.copy(description = description)
                }
                trimmed.startsWith("version:") -> {
                    val version = trimmed.substringAfter(":").trim()
                    return definition.copy(version = version)
                }
                trimmed.startsWith("author:") -> {
                    val author = trimmed.substringAfter(":").trim()
                    return definition.copy(author = author)
                }
                trimmed == "states:" -> {
                    currentSection = "states"
                }
                trimmed == "transitions:" -> {
                    currentSection = "transitions"
                }
                trimmed == "functions:" -> {
                    currentSection = "functions"
                }
                trimmed == "metadata:" -> {
                    currentSection = "metadata"
                }
                trimmed.startsWith("  ") && currentSection == "states" -> {
                    val state = parseStateLine(trimmed)
                    states.add(state)
                }
                trimmed.startsWith("  ") && currentSection == "transitions" -> {
                    val transition = parseTransitionLine(trimmed)
                    transitions.add(transition)
                }
                trimmed.startsWith("  ") && currentSection == "functions" -> {
                    val function = parseFunctionLine(trimmed)
                    functions.add(function)
                }
                trimmed.startsWith("  ") && currentSection == "metadata" -> {
                    val (key, value) = parseMetadataLine(trimmed)
                    metadata[key] = value
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
        
        sb.appendLine("name: ${definition.name}")
        sb.appendLine("description: ${definition.description}")
        sb.appendLine("version: ${definition.version}")
        sb.appendLine("author: ${definition.author}")
        sb.appendLine()
        
        if (definition.states.isNotEmpty()) {
            sb.appendLine("states:")
            definition.states.forEach { state ->
                sb.appendLine("  - id: ${state.id}")
                sb.appendLine("    name: ${state.name}")
                sb.appendLine("    type: ${state.type.name.lowercase()}")
                if (state.parentId != null) {
                    sb.appendLine("    parentId: ${state.parentId}")
                }
                if (state.isInitial) {
                    sb.appendLine("    isInitial: true")
                }
                if (state.isFinal) {
                    sb.appendLine("    isFinal: true")
                }
                if (state.isError) {
                    sb.appendLine("    isError: true")
                }
                if (state.description.isNotEmpty()) {
                    sb.appendLine("    description: ${state.description}")
                }
                sb.appendLine()
            }
        }
        
        if (definition.transitions.isNotEmpty()) {
            sb.appendLine("transitions:")
            definition.transitions.forEach { transition ->
                sb.appendLine("  - from: ${transition.from}")
                sb.appendLine("    to: ${transition.to}")
                sb.appendLine("    condition: ${transition.condition}")
                if (transition.priority > 0) {
                    sb.appendLine("    priority: ${transition.priority}")
                }
                if (transition.description.isNotEmpty()) {
                    sb.appendLine("    description: ${transition.description}")
                }
                sb.appendLine()
            }
        }
        
        if (definition.functions.isNotEmpty()) {
            sb.appendLine("functions:")
            definition.functions.forEach { function ->
                sb.appendLine("  - stateId: ${function.stateId}")
                sb.appendLine("    functionName: ${function.functionName}")
                if (function.className != null) {
                    sb.appendLine("    className: ${function.className}")
                }
                if (function.description.isNotEmpty()) {
                    sb.appendLine("    description: ${function.description}")
                }
                if (function.priority > 0) {
                    sb.appendLine("    priority: ${function.priority}")
                }
                if (function.timeout > 0) {
                    sb.appendLine("    timeout: ${function.timeout}")
                }
                if (function.retryCount > 0) {
                    sb.appendLine("    retryCount: ${function.retryCount}")
                }
                if (function.async) {
                    sb.appendLine("    async: true")
                }
                if (function.tags.isNotEmpty()) {
                    sb.appendLine("    tags: [${function.tags.joinToString(", ")}]")
                }
                sb.appendLine()
            }
        }
        
        if (definition.metadata.isNotEmpty()) {
            sb.appendLine("metadata:")
            definition.metadata.forEach { (key, value) ->
                sb.appendLine("  $key: $value")
            }
        }
        
        return sb.toString()
    }
    
    private fun parseStateLine(line: String): StateInfo {
        val parts = line.trim().split(":")
        val id = parts[1].trim()
        
        // 简化的解析，实际应该更复杂
        return StateInfo(
            id = id,
            name = id.replace("_", " ").replaceFirstChar { it.uppercase() },
            type = StateType.NORMAL,
            isInitial = id.contains("initial"),
            isFinal = id.contains("final") || id.contains("completed"),
            isError = id.contains("error") || id.contains("failed")
        )
    }
    
    private fun parseTransitionLine(line: String): TransitionInfo {
        val parts = line.trim().split("->")
        val from = parts[0].trim()
        val to = parts[1].trim()
        
        return TransitionInfo(
            from = from,
            to = to,
            condition = TransitionCondition.Success
        )
    }
    
    private fun parseFunctionLine(line: String): FunctionInfo {
        val parts = line.trim().split(":")
        val stateId = parts[1].trim()
        
        return FunctionInfo(
            stateId = stateId,
            functionName = "handle${stateId.replace("_", "").replaceFirstChar { it.uppercase() }}"
        )
    }
    
    private fun parseMetadataLine(line: String): Pair<String, Any> {
        val parts = line.trim().split(":", limit = 2)
        val key = parts[0].trim()
        val value = parts[1].trim()
        
        return key to when {
            value == "true" -> true
            value == "false" -> false
            value.matches(Regex("\\d+")) -> value.toInt()
            value.matches(Regex("\\d+\\.\\d+")) -> value.toDouble()
            else -> value
        }
    }
}

/**
 * DSL构建器
 * 提供更友好的DSL语法
 */
class DslBuilder {
    private val definition = StateDefinition("", "")
    
    fun name(name: String) = this.also { 
        // 这里需要重新构建definition
    }
    
    fun description(description: String) = this.also { 
        // 这里需要重新构建definition
    }
    
    fun version(version: String) = this.also { 
        // 这里需要重新构建definition
    }
    
    fun author(author: String) = this.also { 
        // 这里需要重新构建definition
    }
    
    fun state(
        id: String,
        name: String,
        type: StateType = StateType.NORMAL,
        parentId: String? = null,
        isInitial: Boolean = false,
        isFinal: Boolean = false,
        isError: Boolean = false
    ) = this.also {
        // 添加状态
    }
    
    fun transition(
        from: String,
        to: String,
        condition: TransitionCondition = TransitionCondition.Success,
        priority: Int = 0
    ) = this.also {
        // 添加转移
    }
    
    fun function(
        stateId: String,
        functionName: String,
        className: String? = null,
        priority: Int = 0
    ) = this.also {
        // 添加函数
    }
    
    fun build(): StateDefinition = definition
}
