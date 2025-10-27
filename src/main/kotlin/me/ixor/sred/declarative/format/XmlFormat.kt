package me.ixor.sred.declarative.format

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XML格式解析器
 * 支持XML格式定义状态流转
 */
object XmlFormat : StateDefinitionFormat {
    
    override fun parse(content: String): StateDefinition {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val inputSource = org.xml.sax.InputSource(StringReader(content))
        val document = builder.parse(inputSource)
        
        val root = document.documentElement
        
        val name = getTextContent(root, "name") ?: ""
        val description = getTextContent(root, "description") ?: ""
        val version = getTextContent(root, "version") ?: "1.0.0"
        val author = getTextContent(root, "author") ?: ""
        
        val states = parseStates(root.getElementsByTagName("state"))
        val transitions = parseTransitions(root.getElementsByTagName("transition"))
        val functions = parseFunctions(root.getElementsByTagName("function"))
        val metadata = parseMetadata(root.getElementsByTagName("metadata"))
        
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
        
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<stateDefinition>")
        sb.appendLine("  <name>${definition.name}</name>")
        sb.appendLine("  <description>${definition.description}</description>")
        sb.appendLine("  <version>${definition.version}</version>")
        sb.appendLine("  <author>${definition.author}</author>")
        
        if (definition.states.isNotEmpty()) {
            sb.appendLine("  <states>")
            definition.states.forEach { state ->
                sb.appendLine("    <state>")
                sb.appendLine("      <id>${state.id}</id>")
                sb.appendLine("      <name>${state.name}</name>")
                sb.appendLine("      <type>${state.type.name.lowercase()}</type>")
                if (state.parentId != null) {
                    sb.appendLine("      <parentId>${state.parentId}</parentId>")
                }
                if (state.isInitial) {
                    sb.appendLine("      <isInitial>true</isInitial>")
                }
                if (state.isFinal) {
                    sb.appendLine("      <isFinal>true</isFinal>")
                }
                if (state.isError) {
                    sb.appendLine("      <isError>true</isError>")
                }
                if (state.description.isNotEmpty()) {
                    sb.appendLine("      <description>${state.description}</description>")
                }
                sb.appendLine("    </state>")
            }
            sb.appendLine("  </states>")
        }
        
        if (definition.transitions.isNotEmpty()) {
            sb.appendLine("  <transitions>")
            definition.transitions.forEach { transition ->
                sb.appendLine("    <transition>")
                sb.appendLine("      <from>${transition.from}</from>")
                sb.appendLine("      <to>${transition.to}</to>")
                sb.appendLine("      <condition>${transition.condition}</condition>")
                if (transition.priority > 0) {
                    sb.appendLine("      <priority>${transition.priority}</priority>")
                }
                if (transition.description.isNotEmpty()) {
                    sb.appendLine("      <description>${transition.description}</description>")
                }
                sb.appendLine("    </transition>")
            }
            sb.appendLine("  </transitions>")
        }
        
        if (definition.functions.isNotEmpty()) {
            sb.appendLine("  <functions>")
            definition.functions.forEach { function ->
                sb.appendLine("    <function>")
                sb.appendLine("      <stateId>${function.stateId}</stateId>")
                sb.appendLine("      <functionName>${function.functionName}</functionName>")
                if (function.className != null) {
                    sb.appendLine("      <className>${function.className}</className>")
                }
                if (function.description.isNotEmpty()) {
                    sb.appendLine("      <description>${function.description}</description>")
                }
                if (function.priority > 0) {
                    sb.appendLine("      <priority>${function.priority}</priority>")
                }
                if (function.timeout > 0) {
                    sb.appendLine("      <timeout>${function.timeout}</timeout>")
                }
                if (function.retryCount > 0) {
                    sb.appendLine("      <retryCount>${function.retryCount}</retryCount>")
                }
                if (function.async) {
                    sb.appendLine("      <async>true</async>")
                }
                if (function.tags.isNotEmpty()) {
                    sb.appendLine("      <tags>${function.tags.joinToString(",")}</tags>")
                }
                sb.appendLine("    </function>")
            }
            sb.appendLine("  </functions>")
        }
        
        if (definition.metadata.isNotEmpty()) {
            sb.appendLine("  <metadata>")
            definition.metadata.forEach { (key, value) ->
                sb.appendLine("    <$key>$value</$key>")
            }
            sb.appendLine("  </metadata>")
        }
        
        sb.appendLine("</stateDefinition>")
        
        return sb.toString()
    }
    
    private fun parseStates(nodeList: NodeList): List<StateInfo> {
        val states = mutableListOf<StateInfo>()
        
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i) as Element
            val state = StateInfo(
                id = getTextContent(node, "id") ?: "",
                name = getTextContent(node, "name") ?: "",
                type = parseStateType(getTextContent(node, "type") ?: "normal"),
                parentId = getTextContent(node, "parentId"),
                isInitial = getTextContent(node, "isInitial") == "true",
                isFinal = getTextContent(node, "isFinal") == "true",
                isError = getTextContent(node, "isError") == "true",
                description = getTextContent(node, "description") ?: ""
            )
            states.add(state)
        }
        
        return states
    }
    
    private fun parseTransitions(nodeList: NodeList): List<TransitionInfo> {
        val transitions = mutableListOf<TransitionInfo>()
        
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i) as Element
            val transition = TransitionInfo(
                from = getTextContent(node, "from") ?: "",
                to = getTextContent(node, "to") ?: "",
                condition = parseTransitionCondition(getTextContent(node, "condition") ?: "success"),
                priority = getTextContent(node, "priority")?.toIntOrNull() ?: 0,
                description = getTextContent(node, "description") ?: ""
            )
            transitions.add(transition)
        }
        
        return transitions
    }
    
    private fun parseFunctions(nodeList: NodeList): List<FunctionInfo> {
        val functions = mutableListOf<FunctionInfo>()
        
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i) as Element
            val function = FunctionInfo(
                stateId = getTextContent(node, "stateId") ?: "",
                functionName = getTextContent(node, "functionName") ?: "",
                className = getTextContent(node, "className"),
                description = getTextContent(node, "description") ?: "",
                priority = getTextContent(node, "priority")?.toIntOrNull() ?: 0,
                timeout = getTextContent(node, "timeout")?.toLongOrNull() ?: 0L,
                retryCount = getTextContent(node, "retryCount")?.toIntOrNull() ?: 0,
                async = getTextContent(node, "async") == "true",
                tags = getTextContent(node, "tags")?.split(",")?.map { it.trim() } ?: emptyList()
            )
            functions.add(function)
        }
        
        return functions
    }
    
    private fun parseMetadata(nodeList: NodeList): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>()
        
        if (nodeList.length > 0) {
            val metadataNode = nodeList.item(0) as Element
            val children = metadataNode.childNodes
            
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    val element = child as Element
                    val key = element.tagName
                    val value = element.textContent
                    metadata[key] = value
                }
            }
        }
        
        return metadata
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
    
    private fun getTextContent(element: Element, tagName: String): String? {
        val nodeList = element.getElementsByTagName(tagName)
        return if (nodeList.length > 0) {
            nodeList.item(0).textContent
        } else {
            null
        }
    }
}
