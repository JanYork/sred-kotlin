package me.ixor.sred.declarative.format

import java.util.regex.Pattern

/**
 * 简化的JSON格式解析器
 * 不依赖kotlinx.serialization
 */
object SimpleJsonFormat : StateDefinitionFormat {
    
    override fun parse(content: String): StateDefinition {
        val json = content.trim()
        
        val name = extractString(json, "name") ?: ""
        val description = extractString(json, "description") ?: ""
        val version = extractString(json, "version") ?: "1.0.0"
        val author = extractString(json, "author") ?: ""
        
        val states = parseStates(json)
        val transitions = parseTransitions(json)
        val functions = parseFunctions(json)
        val metadata = parseMetadata(json)
        
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
    
    override fun serialize(definition: StateDefinition): String {
        val sb = StringBuilder()
        
        sb.appendLine("{")
        sb.appendLine("  \"name\": \"${definition.name}\",")
        sb.appendLine("  \"description\": \"${definition.description}\",")
        sb.appendLine("  \"version\": \"${definition.version}\",")
        sb.appendLine("  \"author\": \"${definition.author}\",")
        
        if (definition.states.isNotEmpty()) {
            sb.appendLine("  \"states\": [")
            definition.states.forEachIndexed { index, state ->
                sb.appendLine("    {")
                sb.appendLine("      \"id\": \"${state.id}\",")
                sb.appendLine("      \"name\": \"${state.name}\",")
                sb.appendLine("      \"type\": \"${state.type.name.lowercase()}\"")
                if (state.parentId != null) {
                    sb.appendLine(",\"parentId\": \"${state.parentId}\"")
                }
                if (state.isInitial) {
                    sb.appendLine(",\"isInitial\": true")
                }
                if (state.isFinal) {
                    sb.appendLine(",\"isFinal\": true")
                }
                if (state.isError) {
                    sb.appendLine(",\"isError\": true")
                }
                if (state.description.isNotEmpty()) {
                    sb.appendLine(",\"description\": \"${state.description}\"")
                }
                sb.appendLine("    }${if (index < definition.states.size - 1) "," else ""}")
            }
            sb.appendLine("  ],")
        }
        
        if (definition.transitions.isNotEmpty()) {
            sb.appendLine("  \"transitions\": [")
            definition.transitions.forEachIndexed { index, transition ->
                sb.appendLine("    {")
                sb.appendLine("      \"from\": \"${transition.from}\",")
                sb.appendLine("      \"to\": \"${transition.to}\",")
                sb.appendLine("      \"condition\": \"${transition.condition}\"")
                if (transition.priority > 0) {
                    sb.appendLine(",\"priority\": ${transition.priority}")
                }
                if (transition.description.isNotEmpty()) {
                    sb.appendLine(",\"description\": \"${transition.description}\"")
                }
                sb.appendLine("    }${if (index < definition.transitions.size - 1) "," else ""}")
            }
            sb.appendLine("  ],")
        }
        
        if (definition.functions.isNotEmpty()) {
            sb.appendLine("  \"functions\": [")
            definition.functions.forEachIndexed { index, function ->
                sb.appendLine("    {")
                sb.appendLine("      \"stateId\": \"${function.stateId}\",")
                sb.appendLine("      \"functionName\": \"${function.functionName}\"")
                if (function.className != null) {
                    sb.appendLine(",\"className\": \"${function.className}\"")
                }
                if (function.description.isNotEmpty()) {
                    sb.appendLine(",\"description\": \"${function.description}\"")
                }
                if (function.priority > 0) {
                    sb.appendLine(",\"priority\": ${function.priority}")
                }
                if (function.timeout > 0) {
                    sb.appendLine(",\"timeout\": ${function.timeout}")
                }
                if (function.retryCount > 0) {
                    sb.appendLine(",\"retryCount\": ${function.retryCount}")
                }
                if (function.async) {
                    sb.appendLine(",\"async\": true")
                }
                if (function.tags.isNotEmpty()) {
                    sb.appendLine(",\"tags\": [${function.tags.joinToString(",") { "\"$it\"" }}]")
                }
                sb.appendLine("    }${if (index < definition.functions.size - 1) "," else ""}")
            }
            sb.appendLine("  ],")
        }
        
        if (definition.metadata.isNotEmpty()) {
            sb.appendLine("  \"metadata\": {")
            definition.metadata.entries.forEachIndexed { index, (key, value) ->
                val valueStr = when (value) {
                    is String -> "\"$value\""
                    is Boolean -> value.toString()
                    is Number -> value.toString()
                    else -> "\"$value\""
                }
                sb.appendLine("    \"$key\": $valueStr${if (index < definition.metadata.size - 1) "," else ""}")
            }
            sb.appendLine("  }")
        }
        
        sb.appendLine("}")
        
        return sb.toString()
    }
    
    private fun extractString(json: String, key: String): String? {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1) else null
    }
    
    private fun parseStates(json: String): List<StateInfo> {
        val states = mutableListOf<StateInfo>()
        val statesPattern = Pattern.compile("\"states\"\\s*:\\s*\\[([^\\]]+)\\]")
        val matcher = statesPattern.matcher(json)
        
        if (matcher.find()) {
            val statesContent = matcher.group(1)
            val statePattern = Pattern.compile("\\{[^}]+\\}")
            val stateMatcher = statePattern.matcher(statesContent)
            
            while (stateMatcher.find()) {
                val stateJson = stateMatcher.group()
                val state = parseState(stateJson)
                if (state != null) {
                    states.add(state)
                }
            }
        }
        
        return states
    }
    
    private fun parseState(stateJson: String): StateInfo? {
        val id = extractString(stateJson, "id") ?: return null
        val name = extractString(stateJson, "name") ?: ""
        val type = extractString(stateJson, "type") ?: "normal"
        val parentId = extractString(stateJson, "parentId")
        val isInitial = stateJson.contains("\"isInitial\":\\s*true")
        val isFinal = stateJson.contains("\"isFinal\":\\s*true")
        val isError = stateJson.contains("\"isError\":\\s*true")
        val description = extractString(stateJson, "description") ?: ""
        
        return StateInfo(
            id = id,
            name = name,
            type = parseStateType(type),
            parentId = parentId,
            isInitial = isInitial,
            isFinal = isFinal,
            isError = isError,
            description = description
        )
    }
    
    private fun parseTransitions(json: String): List<TransitionInfo> {
        val transitions = mutableListOf<TransitionInfo>()
        val transitionsPattern = Pattern.compile("\"transitions\"\\s*:\\s*\\[([^\\]]+)\\]")
        val matcher = transitionsPattern.matcher(json)
        
        if (matcher.find()) {
            val transitionsContent = matcher.group(1)
            val transitionPattern = Pattern.compile("\\{[^}]+\\}")
            val transitionMatcher = transitionPattern.matcher(transitionsContent)
            
            while (transitionMatcher.find()) {
                val transitionJson = transitionMatcher.group()
                val transition = parseTransition(transitionJson)
                if (transition != null) {
                    transitions.add(transition)
                }
            }
        }
        
        return transitions
    }
    
    private fun parseTransition(transitionJson: String): TransitionInfo? {
        val from = extractString(transitionJson, "from") ?: return null
        val to = extractString(transitionJson, "to") ?: return null
        val condition = extractString(transitionJson, "condition") ?: "success"
        val priority = extractInt(transitionJson, "priority") ?: 0
        val description = extractString(transitionJson, "description") ?: ""
        
        return TransitionInfo(
            from = from,
            to = to,
            condition = parseTransitionCondition(condition),
            priority = priority,
            description = description
        )
    }
    
    private fun parseFunctions(json: String): List<FunctionInfo> {
        val functions = mutableListOf<FunctionInfo>()
        val functionsPattern = Pattern.compile("\"functions\"\\s*:\\s*\\[([^\\]]+)\\]")
        val matcher = functionsPattern.matcher(json)
        
        if (matcher.find()) {
            val functionsContent = matcher.group(1)
            val functionPattern = Pattern.compile("\\{[^}]+\\}")
            val functionMatcher = functionPattern.matcher(functionsContent)
            
            while (functionMatcher.find()) {
                val functionJson = functionMatcher.group()
                val function = parseFunction(functionJson)
                if (function != null) {
                    functions.add(function)
                }
            }
        }
        
        return functions
    }
    
    private fun parseFunction(functionJson: String): FunctionInfo? {
        val stateId = extractString(functionJson, "stateId") ?: return null
        val functionName = extractString(functionJson, "functionName") ?: ""
        val className = extractString(functionJson, "className")
        val description = extractString(functionJson, "description") ?: ""
        val priority = extractInt(functionJson, "priority") ?: 0
        val timeout = extractLong(functionJson, "timeout") ?: 0L
        val retryCount = extractInt(functionJson, "retryCount") ?: 0
        val async = functionJson.contains("\"async\":\\s*true")
        val tags = extractStringArray(functionJson, "tags")
        
        return FunctionInfo(
            stateId = stateId,
            functionName = functionName,
            className = className,
            description = description,
            priority = priority,
            timeout = timeout,
            retryCount = retryCount,
            async = async,
            tags = tags
        )
    }
    
    private fun parseMetadata(json: String): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()
        val metadataPattern = Pattern.compile("\"metadata\"\\s*:\\s*\\{([^}]+)\\}")
        val matcher = metadataPattern.matcher(json)
        
        if (matcher.find()) {
            val metadataContent = matcher.group(1)
            val keyValuePattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*([^,}]+)")
            val keyValueMatcher = keyValuePattern.matcher(metadataContent)
            
            while (keyValueMatcher.find()) {
                val key = keyValueMatcher.group(1)
                val value = keyValueMatcher.group(2).trim()
                
                val parsedValue = when {
                    value.startsWith("\"") && value.endsWith("\"") -> value.substring(1, value.length - 1)
                    value == "true" -> true
                    value == "false" -> false
                    value.matches(Regex("\\d+")) -> value.toInt()
                    value.matches(Regex("\\d+\\.\\d+")) -> value.toDouble()
                    else -> value
                }
                
                metadata[key] = parsedValue
            }
        }
        
        return metadata
    }
    
    private fun extractInt(json: String, key: String): Int? {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*(\\d+)")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1).toInt() else null
    }
    
    private fun extractLong(json: String, key: String): Long? {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*(\\d+)")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1).toLong() else null
    }
    
    private fun extractStringArray(json: String, key: String): List<String> {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*\\[([^\\]]+)\\]")
        val matcher = pattern.matcher(json)
        
        if (matcher.find()) {
            val arrayContent = matcher.group(1)
            val stringPattern = Pattern.compile("\"([^\"]+)\"")
            val stringMatcher = stringPattern.matcher(arrayContent)
            val strings = mutableListOf<String>()
            
            while (stringMatcher.find()) {
                strings.add(stringMatcher.group(1))
            }
            
            return strings
        }
        
        return emptyList()
    }
    
    private fun parseStateType(type: String): StateType {
        return when (type.lowercase()) {
            "initial" -> StateType.INITIAL
            "normal" -> StateType.NORMAL
            "final" -> StateType.FINAL
            "error" -> StateType.ERROR
            else -> StateType.NORMAL
        }
    }
    
    private fun parseTransitionCondition(condition: String): TransitionCondition {
        return when (condition.lowercase()) {
            "success" -> TransitionCondition.Success
            "failure" -> TransitionCondition.Failure
            else -> TransitionCondition.Custom(condition)
        }
    }
}
