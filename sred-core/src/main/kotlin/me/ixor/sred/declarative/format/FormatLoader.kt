package me.ixor.sred.declarative.format

import me.ixor.sred.core.ConfigurationException
import me.ixor.sred.core.SecurityException
import me.ixor.sred.core.ResourceException
import me.ixor.sred.core.logger
import me.ixor.sred.declarative.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * 格式加载器 - 统一的格式加载接口
 * 支持从文件、URL或字符串加载JSON/XML/YAML/DSL格式的状态定义
 */
object FormatLoader {
    
    private val log = logger<FormatLoader>()
    
    // 配置常量
    private const val DEFAULT_CONNECT_TIMEOUT_MS = 10000 // 10秒
    private const val DEFAULT_READ_TIMEOUT_MS = 30000 // 30秒
    private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    private val ALLOWED_PROTOCOLS = setOf("http", "https")
    
    /**
     * 从路径加载状态定义（自动识别文件路径或URL）
     * 支持：
     * - 本地文件路径：如 "config.json", "/path/to/config.json"
     * - URL路径：如 "http://example.com/config.json", "https://example.com/config.json"
     * 
     * @param path 文件路径或URL
     * @return StateFlow
     */
    fun load(path: String): StateFlow {
        log.debug { "Loading configuration from: $path" }
        return try {
            when {
                path.startsWith("http://") || path.startsWith("https://") -> {
                    loadFromUrl(path)
                }
                else -> {
                    loadFromFile(path)
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to load configuration from: $path" }
            throw when (e) {
                is ConfigurationException, is SecurityException, is ResourceException -> e
                else -> ConfigurationException("Failed to load configuration from: $path", e)
            }
        }
    }
    
    /**
     * 从文件加载状态定义
     * 自动根据文件扩展名识别格式
     */
    fun loadFromFile(filePath: String): StateFlow {
        val file = File(filePath)
        if (!file.exists()) {
            throw ConfigurationException("File not found: $filePath")
        }
        
        if (!file.isFile) {
            throw ConfigurationException("Path is not a file: $filePath")
        }
        
        if (file.length() > MAX_FILE_SIZE_BYTES) {
            throw ResourceException("File size (${file.length()} bytes) exceeds maximum allowed size ($MAX_FILE_SIZE_BYTES bytes): $filePath")
        }
        
        if (!file.canRead()) {
            throw ResourceException("File is not readable: $filePath")
        }
        
        log.debug { "Loading file: $filePath (size: ${file.length()} bytes)" }
        
        val content = try {
            file.readText()
        } catch (e: Exception) {
            throw ResourceException("Failed to read file: $filePath", e)
        }
        
        val extension = file.extension.lowercase()
        return when (extension) {
            "json" -> loadFromString(content, FormatType.JSON)
            "xml" -> loadFromString(content, FormatType.XML)
            "yaml", "yml" -> loadFromString(content, FormatType.YAML)
            "dsl", "txt" -> loadFromString(content, FormatType.DSL)
            else -> throw ConfigurationException("Unsupported file format: $extension (supported: json, xml, yaml, yml, dsl, txt)")
        }
    }
    
    /**
     * 从URL加载状态定义
     * 自动根据URL路径的扩展名识别格式
     * 
     * 安全特性：
     * - 只允许 HTTP/HTTPS 协议
     * - 连接和读取超时设置
     * - 文件大小限制
     */
    fun loadFromUrl(urlString: String): StateFlow {
        log.debug { "Loading configuration from URL: $urlString" }
        
        val url = try {
            URL(urlString)
        } catch (e: Exception) {
            throw ConfigurationException("Invalid URL: $urlString", e)
        }
        
        // 安全检查：只允许 HTTP/HTTPS 协议
        val protocol = url.protocol.lowercase()
        if (protocol !in ALLOWED_PROTOCOLS) {
            throw SecurityException("URL protocol '$protocol' is not allowed. Only ${ALLOWED_PROTOCOLS.joinToString(", ")} are supported")
        }
        
        // 打开连接并设置超时
        val connection = try {
            url.openConnection() as? HttpURLConnection
                ?: throw SecurityException("Unsupported connection type for URL: $urlString")
        } catch (e: Exception) {
            throw ResourceException("Failed to open connection to: $urlString", e)
        }
        
        try {
            connection.connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS
            connection.readTimeout = DEFAULT_READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            
            // 设置 User-Agent
            connection.setRequestProperty("User-Agent", "SRED-ConfigLoader/1.0")
            
            // 对于 HTTPS，验证证书
            if (connection is HttpsURLConnection) {
                // 使用默认的 SSL 上下文（包含系统信任的证书）
                // 如果需要自定义验证，可以在这里添加
            }
            
            // 检查响应代码
            val responseCode = try {
                connection.responseCode
            } catch (e: Exception) {
                throw ResourceException("Failed to connect to URL: $urlString", e)
            }
            
            if (responseCode !in 200..299) {
                throw ResourceException("HTTP error $responseCode when loading from URL: $urlString")
            }
            
            // 检查内容大小
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_FILE_SIZE_BYTES) {
                throw ResourceException("Content size ($contentLength bytes) exceeds maximum allowed size ($MAX_FILE_SIZE_BYTES bytes)")
            }
            
            // 读取内容
            val content = try {
                connection.inputStream.use { input ->
                    val reader = input.bufferedReader()
                    val buffer = StringBuilder()
                    var totalRead = 0L
                    
                    reader.lineSequence().forEach { line ->
                        totalRead += line.length + 1 // +1 for newline
                        if (totalRead > MAX_FILE_SIZE_BYTES) {
                            throw ResourceException("Content size exceeds maximum allowed size ($MAX_FILE_SIZE_BYTES bytes)")
                        }
                        buffer.append(line).append("\n")
                    }
                    buffer.toString()
                }
            } catch (e: Exception) {
                throw when (e) {
                    is ResourceException -> e
                    else -> ResourceException("Failed to read content from URL: $urlString", e)
                }
            }
            
            log.debug { "Successfully loaded ${content.length} bytes from URL: $urlString" }
            
            // 从URL路径提取扩展名
            val path = url.path
            val extension = if (path.contains(".")) {
                path.substringAfterLast(".").lowercase()
            } else {
                throw ConfigurationException("Cannot determine file format from URL path: $urlString")
            }
            
            return when (extension) {
                "json" -> loadFromString(content, FormatType.JSON)
                "xml" -> loadFromString(content, FormatType.XML)
                "yaml", "yml" -> loadFromString(content, FormatType.YAML)
                "dsl", "txt" -> loadFromString(content, FormatType.DSL)
                else -> throw ConfigurationException("Unsupported file format: $extension (from URL: $urlString). Supported: json, xml, yaml, yml, dsl, txt")
            }
        } finally {
            connection.disconnect()
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

