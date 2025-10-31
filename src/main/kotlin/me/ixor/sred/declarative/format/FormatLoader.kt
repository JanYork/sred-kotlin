package me.ixor.sred.declarative.format

import me.ixor.sred.declarative.*
import java.io.File

/**
 * 格式加载器 - 统一的格式加载接口
 * 支持从文件或字符串加载JSON/XML/YAML/DSL格式的状态定义
 */
object FormatLoader {
    
    /**
     * 从文件加载状态定义
     * 自动根据文件扩展名识别格式
     */
    fun loadFromFile(filePath: String): StateFlow {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }
        
        val content = file.readText()
        val extension = file.extension.lowercase()
        
        return when (extension) {
            "json" -> loadFromString(content, FormatType.JSON)
            "xml" -> loadFromString(content, FormatType.XML)
            "yaml", "yml" -> loadFromString(content, FormatType.YAML)
            "dsl", "txt" -> loadFromString(content, FormatType.DSL)
            else -> throw IllegalArgumentException("Unsupported file format: $extension")
        }
    }
    
    /**
     * 从字符串加载状态定义
     */
    fun loadFromString(content: String, format: FormatType): StateFlow {
        val formatParser = when (format) {
            FormatType.JSON -> JsonFormat
            FormatType.XML -> XmlFormat
            FormatType.YAML -> YamlFormat
            FormatType.DSL -> DslFormat
        }
        
        return StateDefinitionParser.parse(formatParser, content)
    }
    
    /**
     * 保存状态定义到文件
     */
    fun saveToFile(flow: StateFlow, filePath: String, format: FormatType? = null) {
        val file = File(filePath)
        val actualFormat = format ?: detectFormatFromExtension(file.extension)
        
        val formatParser = when (actualFormat) {
            FormatType.JSON -> JsonFormat
            FormatType.XML -> XmlFormat
            FormatType.YAML -> YamlFormat
            FormatType.DSL -> DslFormat
        }
        
        // 将StateFlow转换为StateDefinition
        val definition = convertToStateDefinition(flow)
        val content = formatParser.serialize(definition)
        
        file.writeText(content)
    }
    
    /**
     * 支持的格式类型
     */
    enum class FormatType {
        JSON,
        XML,
        YAML,
        DSL
    }
    
    private fun detectFormatFromExtension(extension: String): FormatType {
        return when (extension.lowercase()) {
            "json" -> FormatType.JSON
            "xml" -> FormatType.XML
            "yaml", "yml" -> FormatType.YAML
            "dsl", "txt" -> FormatType.DSL
            else -> FormatType.JSON // 默认JSON
        }
    }
    
    private fun convertToStateDefinition(flow: StateFlow): StateDefinition {
        val states = flow.states.values.map { stateDef ->
            StateInfo(
                id = stateDef.id,
                name = stateDef.name,
                type = convertToStateType(stateDef.type),
                parentId = stateDef.parentId,
                isInitial = stateDef.isInitial,
                isFinal = stateDef.isFinal,
                isError = stateDef.isError
            )
        }
        
        val transitions = flow.transitions.values.flatten().map { transitionDef ->
            TransitionInfo(
                from = transitionDef.from,
                to = transitionDef.to,
                condition = convertToTransitionCondition(transitionDef.condition),
                priority = transitionDef.priority
            )
        }
        
        // 函数信息需要从外部提供，这里暂时为空
        val functions = emptyList<FunctionInfo>()
        
        return StateDefinition(
            name = "StateFlow",
            description = "Generated from StateFlow",
            states = states,
            transitions = transitions,
            functions = functions
        )
    }
    
    private fun convertToStateType(type: StateFlow.StateType): StateType {
        return when (type) {
            StateFlow.StateType.INITIAL -> StateType.INITIAL
            StateFlow.StateType.NORMAL -> StateType.NORMAL
            StateFlow.StateType.FINAL -> StateType.FINAL
            StateFlow.StateType.ERROR -> StateType.ERROR
        }
    }
    
    private fun convertToTransitionCondition(condition: StateFlow.TransitionCondition): TransitionCondition {
        return when (condition) {
            is StateFlow.TransitionCondition.Success -> TransitionCondition.Success
            is StateFlow.TransitionCondition.Failure -> TransitionCondition.Failure
            is StateFlow.TransitionCondition.Custom -> TransitionCondition.Custom("custom")
        }
    }
}

