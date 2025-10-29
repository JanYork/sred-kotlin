package me.ixor.sred.declarative.format

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * JSON格式解析器
 * 使用Jackson库处理JSON格式的状态定义
 */
object JsonFormat : StateDefinitionFormat {
    
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    
    override fun parse(content: String): StateDefinition {
        return try {
            objectMapper.readValue<StateDefinition>(content)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse JSON: ${e.message}", e)
        }
    }
    
    override fun serialize(definition: StateDefinition): String {
        return try {
            objectMapper.writeValueAsString(definition)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to serialize to JSON: ${e.message}", e)
        }
    }
}

