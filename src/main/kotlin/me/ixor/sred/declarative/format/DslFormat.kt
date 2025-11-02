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
        // 支持多种格式：
        //   - id: state_id
        //   - id: state_id, name: State Name, type: INITIAL
        //   - state_id (name: State Name, type: INITIAL, isInitial: true)
        val trimmed = line.trim().removePrefix("-").trim()
        
        var id = ""
        var name = ""
        var type = StateType.NORMAL
        var parentId: String? = null
        var isInitial = false
        var isFinal = false
        var isError = false
        var description = ""
        
        // 提取 id
        val idMatch = Regex("id:\\s*([^,)]+)").find(trimmed)
        id = idMatch?.groupValues?.get(1)?.trim() ?: trimmed.split(":")[0].trim()
        
        // 提取 name
        val nameMatch = Regex("name:\\s*([^,)]+)").find(trimmed)
        name = nameMatch?.groupValues?.get(1)?.trim()?.replace("\"", "")?.replace("'", "")
            ?: id.replace("_", " ").replaceFirstChar { it.uppercase() }
        
        // 提取 type
        val typeMatch = Regex("type:\\s*([^,)]+)").find(trimmed)
        val typeStr = typeMatch?.groupValues?.get(1)?.trim()?.uppercase() ?: "NORMAL"
        type = try {
            StateType.valueOf(typeStr)
        } catch (e: Exception) {
            StateType.NORMAL
        }
        
        // 提取 parentId
        val parentIdMatch = Regex("parentId:\\s*([^,)]+)").find(trimmed)
        parentId = parentIdMatch?.groupValues?.get(1)?.trim()
        
        // 提取布尔标志
        isInitial = Regex("isInitial:\\s*true").containsMatchIn(trimmed) || 
                   id.contains("initial", ignoreCase = true)
        isFinal = Regex("isFinal:\\s*true").containsMatchIn(trimmed) ||
                  id.contains("final", ignoreCase = true) || id.contains("completed", ignoreCase = true)
        isError = Regex("isError:\\s*true").containsMatchIn(trimmed) ||
                  id.contains("error", ignoreCase = true) || id.contains("failed", ignoreCase = true)
        
        // 提取 description
        val descMatch = Regex("description:\\s*[\"']?([^,\"')]+)[\"']?").find(trimmed)
        description = descMatch?.groupValues?.get(1)?.trim() ?: ""
        
        return StateInfo(
            id = id,
            name = name,
            type = type,
            parentId = parentId,
            isInitial = isInitial,
            isFinal = isFinal,
            isError = isError,
            description = description
        )
    }
    
    private fun parseTransitionLine(line: String): TransitionInfo {
        // 支持多种格式：
        //   - from -> to
        //   - from -> to (condition: Success, priority: 1)
        val trimmed = line.trim().removePrefix("-").trim()
        
        // 提取 from 和 to
        val arrowMatch = Regex("([^->]+)->([^(]+)").find(trimmed)
        val from = arrowMatch?.groupValues?.get(1)?.trim() ?: trimmed.split("->")[0].trim()
        val to = arrowMatch?.groupValues?.get(2)?.trim()?.split("(")?.get(0)?.trim()
            ?: trimmed.split("->")[1].trim().split("(")[0].trim()
        
        // 提取 condition
        val conditionMatch = Regex("condition:\\s*([^,)]+)").find(trimmed)
        val conditionStr = conditionMatch?.groupValues?.get(1)?.trim()?.uppercase() ?: "SUCCESS"
        val condition = when (conditionStr) {
            "SUCCESS" -> TransitionCondition.Success
            "FAILURE" -> TransitionCondition.Failure
            else -> TransitionCondition.Custom(conditionStr)
        }
        
        // 提取 priority
        val priorityMatch = Regex("priority:\\s*(\\d+)").find(trimmed)
        val priority = priorityMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        
        // 提取 description
        val descMatch = Regex("description:\\s*[\"']?([^,\"')]+)[\"']?").find(trimmed)
        val description = descMatch?.groupValues?.get(1)?.trim() ?: ""
        
        return TransitionInfo(
            from = from,
            to = to,
            condition = condition,
            priority = priority,
            description = description
        )
    }
    
    private fun parseFunctionLine(line: String): FunctionInfo {
        // 支持多种格式：
        //   - stateId: state_id
        //   - stateId: state_id, functionName: handleState, priority: 1
        val trimmed = line.trim().removePrefix("-").trim()
        
        // 提取 stateId
        val stateIdMatch = Regex("stateId:\\s*([^,]+)").find(trimmed)
        val stateId = stateIdMatch?.groupValues?.get(1)?.trim()
            ?: trimmed.split(":")[1].trim().split(",")[0].trim()
        
        // 提取 functionName
        val functionNameMatch = Regex("functionName:\\s*([^,]+)").find(trimmed)
        val functionName = functionNameMatch?.groupValues?.get(1)?.trim()?.replace("\"", "")?.replace("'", "")
            ?: "handle${stateId.replace("_", "").replaceFirstChar { it.uppercase() }}"
        
        // 提取其他属性
        val classNameMatch = Regex("className:\\s*([^,]+)").find(trimmed)
        val className = classNameMatch?.groupValues?.get(1)?.trim()?.replace("\"", "")?.replace("'", "")
        
        val descriptionMatch = Regex("description:\\s*[\"']?([^,\"']+)[\"']?").find(trimmed)
        val description = descriptionMatch?.groupValues?.get(1)?.trim() ?: ""
        
        val priorityMatch = Regex("priority:\\s*(\\d+)").find(trimmed)
        val priority = priorityMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        
        val timeoutMatch = Regex("timeout:\\s*(\\d+)").find(trimmed)
        val timeout = timeoutMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        
        val retryCountMatch = Regex("retryCount:\\s*(\\d+)").find(trimmed)
        val retryCount = retryCountMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        
        val asyncMatch = Regex("async:\\s*(true|false)").find(trimmed)
        val async = asyncMatch?.groupValues?.get(1)?.toBoolean() ?: false
        
        return FunctionInfo(
            stateId = stateId,
            functionName = functionName,
            className = className,
            description = description,
            priority = priority,
            timeout = timeout,
            retryCount = retryCount,
            async = async
        )
    }
    
    private fun parseMetadataLine(line: String): Pair<String, Any> {
        val trimmed = line.trim()
        val parts = trimmed.split(":", limit = 2)
        val key = parts[0].trim()
        val value = parts.getOrNull(1)?.trim() ?: ""
        
        // 移除引号
        val cleanValue = value.removeSurrounding("\"").removeSurrounding("'")
        
        return key to when {
            cleanValue == "true" -> true
            cleanValue == "false" -> false
            cleanValue.matches(Regex("-?\\d+")) -> cleanValue.toInt()
            cleanValue.matches(Regex("-?\\d+\\.\\d+")) -> cleanValue.toDouble()
            cleanValue.startsWith("[") && cleanValue.endsWith("]") -> {
                // 解析数组
                cleanValue.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            }
            else -> cleanValue
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
