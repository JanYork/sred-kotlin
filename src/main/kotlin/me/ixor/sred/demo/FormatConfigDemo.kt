package me.ixor.sred.demo

import me.ixor.sred.core.*
import me.ixor.sred.declarative.*
import me.ixor.sred.declarative.format.*

/**
 * 多格式配置演示
 * 展示DSL、XML、JSON等格式的状态定义
 */
object FormatConfigDemo {
    
    /**
     * 运行多格式配置演示
     */
    suspend fun runDemo(): DemoFramework.DemoResult {
        val config = DemoFramework.createConfig(
            name = "多格式配置演示",
            description = "展示如何使用DSL、XML、JSON等格式定义状态流转",
            category = "配置管理",
            timeout = 30000L
        )
        
        return DemoFramework.runDemo(config) {
            DemoFramework.step("DSL格式配置演示") {
                val dslContent = """
                    name: 订单处理流程
                    description: 订单处理的完整流程定义
                    version: 1.0.0
                    author: SRED Team
                    
                    states:
                      - id: order_pending
                        name: 订单待处理
                        type: initial
                        isInitial: true
                      - id: order_processing
                        name: 订单处理中
                        type: normal
                      - id: order_completed
                        name: 订单完成
                        type: final
                        isFinal: true
                      - id: order_failed
                        name: 订单失败
                        type: error
                        isError: true
                      - id: payment_processing
                        name: 支付处理
                        type: normal
                        parentId: order_processing
                      - id: shipping_preparation
                        name: 发货准备
                        type: normal
                        parentId: order_processing
                    
                    transitions:
                      - from: order_pending
                        to: order_processing
                        condition: success
                      - from: order_processing
                        to: order_completed
                        condition: success
                      - from: order_processing
                        to: order_failed
                        condition: failure
                      - from: payment_processing
                        to: shipping_preparation
                        condition: success
                      - from: payment_processing
                        to: order_failed
                        condition: failure
                      - from: shipping_preparation
                        to: order_completed
                        condition: success
                      - from: shipping_preparation
                        to: order_failed
                        condition: failure
                    
                    functions:
                      - stateId: payment_processing
                        functionName: processPayment
                        className: PaymentService
                        priority: 1
                        timeout: 10000
                        tags: [payment, financial]
                      - stateId: shipping_preparation
                        functionName: prepareShipping
                        className: ShippingService
                        priority: 2
                        timeout: 5000
                        tags: [shipping, logistics]
                    
                    metadata:
                      timeout: 60000
                      retry_count: 3
                      business_type: ecommerce
                """.trimIndent()
                
                DemoFramework.info("DSL配置内容:")
                println("   ${dslContent.lines().take(10).joinToString("\n   ")}...")
                
                val stateFlow = StateDefinitionParser.parse(DslFormat, dslContent)
                val stateMachine = stateFlow.build()
                
                DemoFramework.success("DSL解析完成，状态数量: ${stateFlow.states.size}")
                DemoFramework.info("转移数量: ${stateFlow.transitions.size}")
            }
            
            DemoFramework.step("XML格式配置演示") {
                val xmlContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <stateDefinition>
                        <name>订单处理流程</name>
                        <description>订单处理的完整流程定义</description>
                        <version>1.0.0</version>
                        <author>SRED Team</author>
                        
                        <states>
                            <state>
                                <id>order_pending</id>
                                <name>订单待处理</name>
                                <type>initial</type>
                                <isInitial>true</isInitial>
                            </state>
                            <state>
                                <id>order_processing</id>
                                <name>订单处理中</name>
                                <type>normal</type>
                            </state>
                            <state>
                                <id>order_completed</id>
                                <name>订单完成</name>
                                <type>final</type>
                                <isFinal>true</isFinal>
                            </state>
                            <state>
                                <id>order_failed</id>
                                <name>订单失败</name>
                                <type>error</type>
                                <isError>true</isError>
                            </state>
                            <state>
                                <id>payment_processing</id>
                                <name>支付处理</name>
                                <type>normal</type>
                                <parentId>order_processing</parentId>
                            </state>
                            <state>
                                <id>shipping_preparation</id>
                                <name>发货准备</name>
                                <type>normal</type>
                                <parentId>order_processing</parentId>
                            </state>
                        </states>
                        
                        <transitions>
                            <transition>
                                <from>order_pending</from>
                                <to>order_processing</to>
                                <condition>success</condition>
                            </transition>
                            <transition>
                                <from>order_processing</from>
                                <to>order_completed</to>
                                <condition>success</condition>
                            </transition>
                            <transition>
                                <from>order_processing</from>
                                <to>order_failed</to>
                                <condition>failure</condition>
                            </transition>
                            <transition>
                                <from>payment_processing</from>
                                <to>shipping_preparation</to>
                                <condition>success</condition>
                            </transition>
                            <transition>
                                <from>payment_processing</from>
                                <to>order_failed</to>
                                <condition>failure</condition>
                            </transition>
                            <transition>
                                <from>shipping_preparation</from>
                                <to>order_completed</to>
                                <condition>success</condition>
                            </transition>
                            <transition>
                                <from>shipping_preparation</from>
                                <to>order_failed</to>
                                <condition>failure</condition>
                            </transition>
                        </transitions>
                        
                        <functions>
                            <function>
                                <stateId>payment_processing</stateId>
                                <functionName>processPayment</functionName>
                                <className>PaymentService</className>
                                <priority>1</priority>
                                <timeout>10000</timeout>
                                <tags>payment,financial</tags>
                            </function>
                            <function>
                                <stateId>shipping_preparation</stateId>
                                <functionName>prepareShipping</functionName>
                                <className>ShippingService</className>
                                <priority>2</priority>
                                <timeout>5000</timeout>
                                <tags>shipping,logistics</tags>
                            </function>
                        </functions>
                        
                        <metadata>
                            <timeout>60000</timeout>
                            <retry_count>3</retry_count>
                            <business_type>ecommerce</business_type>
                        </metadata>
                    </stateDefinition>
                """.trimIndent()
                
                DemoFramework.info("XML配置内容:")
                println("   ${xmlContent.lines().take(10).joinToString("\n   ")}...")
                
                val stateFlow = StateDefinitionParser.parse(XmlFormat, xmlContent)
                val stateMachine = stateFlow.build()
                
                DemoFramework.success("XML解析完成，状态数量: ${stateFlow.states.size}")
                DemoFramework.info("转移数量: ${stateFlow.transitions.size}")
            }
            
            DemoFramework.step("JSON格式配置演示") {
                val jsonContent = """
                    {
                        "name": "订单处理流程",
                        "description": "订单处理的完整流程定义",
                        "version": "1.0.0",
                        "author": "SRED Team",
                        "states": [
                            {
                                "id": "order_pending",
                                "name": "订单待处理",
                                "type": "initial",
                                "isInitial": true
                            },
                            {
                                "id": "order_processing",
                                "name": "订单处理中",
                                "type": "normal"
                            },
                            {
                                "id": "order_completed",
                                "name": "订单完成",
                                "type": "final",
                                "isFinal": true
                            },
                            {
                                "id": "order_failed",
                                "name": "订单失败",
                                "type": "error",
                                "isError": true
                            },
                            {
                                "id": "payment_processing",
                                "name": "支付处理",
                                "type": "normal",
                                "parentId": "order_processing"
                            },
                            {
                                "id": "shipping_preparation",
                                "name": "发货准备",
                                "type": "normal",
                                "parentId": "order_processing"
                            }
                        ],
                        "transitions": [
                            {
                                "from": "order_pending",
                                "to": "order_processing",
                                "condition": "success"
                            },
                            {
                                "from": "order_processing",
                                "to": "order_completed",
                                "condition": "success"
                            },
                            {
                                "from": "order_processing",
                                "to": "order_failed",
                                "condition": "failure"
                            },
                            {
                                "from": "payment_processing",
                                "to": "shipping_preparation",
                                "condition": "success"
                            },
                            {
                                "from": "payment_processing",
                                "to": "order_failed",
                                "condition": "failure"
                            },
                            {
                                "from": "shipping_preparation",
                                "to": "order_completed",
                                "condition": "success"
                            },
                            {
                                "from": "shipping_preparation",
                                "to": "order_failed",
                                "condition": "failure"
                            }
                        ],
                        "functions": [
                            {
                                "stateId": "payment_processing",
                                "functionName": "processPayment",
                                "className": "PaymentService",
                                "priority": 1,
                                "timeout": 10000,
                                "tags": ["payment", "financial"]
                            },
                            {
                                "stateId": "shipping_preparation",
                                "functionName": "prepareShipping",
                                "className": "ShippingService",
                                "priority": 2,
                                "timeout": 5000,
                                "tags": ["shipping", "logistics"]
                            }
                        ],
                        "metadata": {
                            "timeout": 60000,
                            "retry_count": 3,
                            "business_type": "ecommerce"
                        }
                    }
                """.trimIndent()
                
                DemoFramework.info("JSON配置内容:")
                println("   ${jsonContent.lines().take(10).joinToString("\n   ")}...")
                
                val stateFlow = StateDefinitionParser.parse(SimpleJsonFormat, jsonContent)
                val stateMachine = stateFlow.build()
                
                DemoFramework.success("JSON解析完成，状态数量: ${stateFlow.states.size}")
                DemoFramework.info("转移数量: ${stateFlow.transitions.size}")
            }
            
            DemoFramework.step("格式转换演示") {
                val definition = StateDefinition(
                    name = "订单处理流程",
                    description = "订单处理的完整流程定义",
                    version = "1.0.0",
                    author = "SRED Team",
                    states = listOf(
                        StateInfo("order_pending", "订单待处理", StateType.INITIAL, isInitial = true),
                        StateInfo("order_processing", "订单处理中", StateType.NORMAL),
                        StateInfo("order_completed", "订单完成", StateType.FINAL, isFinal = true),
                        StateInfo("order_failed", "订单失败", StateType.ERROR, isError = true)
                    ),
                    transitions = listOf(
                        TransitionInfo("order_pending", "order_processing", me.ixor.sred.declarative.format.TransitionCondition.Success),
                        TransitionInfo("order_processing", "order_completed", me.ixor.sred.declarative.format.TransitionCondition.Success),
                        TransitionInfo("order_processing", "order_failed", me.ixor.sred.declarative.format.TransitionCondition.Failure)
                    ),
                    functions = listOf(
                        FunctionInfo("payment_processing", "processPayment", "PaymentService", priority = 1),
                        FunctionInfo("shipping_preparation", "prepareShipping", "ShippingService", priority = 2)
                    ),
                    metadata = mapOf(
                        "timeout" to 60000,
                        "retry_count" to 3,
                        "business_type" to "ecommerce"
                    )
                )
                
                DemoFramework.info("转换为DSL格式:")
                val dslContent = DslFormat.serialize(definition)
                println("   ${dslContent.lines().take(5).joinToString("\n   ")}...")
                
                DemoFramework.info("转换为XML格式:")
                val xmlContent = XmlFormat.serialize(definition)
                println("   ${xmlContent.lines().take(5).joinToString("\n   ")}...")
                
                DemoFramework.info("转换为JSON格式:")
                val jsonContent = SimpleJsonFormat.serialize(definition)
                println("   ${jsonContent.lines().take(5).joinToString("\n   ")}...")
                
                DemoFramework.success("格式转换完成")
            }
            
            DemoFramework.step("展示格式特点") {
                DemoFramework.info("各格式特点对比:")
                println("""
                    ┌─────────────────────────────────────────────────────────┐
                    │                    格式特点对比                          │
                    ├─────────────────────────────────────────────────────────┤
                    │ DSL格式    │ 类似YAML，简洁易读，适合人工编辑              │
                    │ XML格式    │ 结构化强，适合工具处理，支持验证              │
                    │ JSON格式   │ 轻量级，适合API交互，广泛支持                │
                    └─────────────────────────────────────────────────────────┘
                    
                    ┌─────────────────────────────────────────────────────────┐
                    │                    使用场景                              │
                    ├─────────────────────────────────────────────────────────┤
                    │ DSL格式    │ 配置文件、文档示例、快速原型                │
                    │ XML格式    │ 企业级配置、复杂结构、工具集成              │
                    │ JSON格式   │ Web API、微服务、前端集成                   │
                    └─────────────────────────────────────────────────────────┘
                """.trimIndent())
            }
        }
    }
}
