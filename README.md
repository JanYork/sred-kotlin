# SRED架构实现

## 状态轮转与事件驱动结合形架构 (State Rotation Event Driven Architecture)

这是一个基于Kotlin实现的SRED架构框架，展示了如何将状态轮转与事件驱动结合，构建自组织、自演化的软件系统。

## 架构概述

SRED架构基于以下核心思想：

1. **状态轮转**：系统以状态为核心自我运行，控制权从开发者转移到运行时系统
2. **事件驱动**：系统被动响应外部刺激和内部变化，事件是状态变化的"原因"
3. **上下文驱动**：状态转移不仅依赖于当前状态与事件，还受上下文环境影响

## 核心组件

### 1. 状态层 (State Layer)
- **State**: 状态接口，定义状态的行为和生命周期
- **StateRegistry**: 状态注册表，管理所有状态
- **StateManager**: 状态管理器，负责状态的生命周期和持久化

### 2. 事件层 (Event Layer)
- **Event**: 事件接口，定义事件的结构和行为
- **EventBus**: 事件总线，负责事件的发布、分发和订阅
- **EventEmitter**: 事件发射器，负责产生和发布事件

### 3. 调度层 (Orchestrator Layer)
- **StateOrchestrator**: 状态调度器，基于上下文决定状态转移
- **StateTransition**: 状态转移，定义状态间的转换规则
- **StateContext**: 状态上下文，包含全局和局部状态信息

## 快速开始

### 1. 构建项目

```bash
mvn clean compile
```

### 2. 运行示例

```bash
mvn exec:java -Dexec.mainClass="me.ixor.sred.Main"
```

### 3. 运行测试

```bash
mvn test
```

## 使用示例

### 创建状态

```kotlin
class MyState : AbstractState(
    id = "my_state",
    name = "我的状态",
    description = "这是一个示例状态"
) {
    override fun canHandle(event: Event, context: StateContext): Boolean {
        return event.type == EventType("my", "my_event")
    }
    
    override suspend fun handleEvent(event: Event, context: StateContext): StateTransitionResult {
        // 处理事件逻辑
        return StateTransitionResult(
            success = true,
            nextStateId = "next_state",
            updatedContext = context
        )
    }
}
```

### 创建事件

```kotlin
val event = EventFactory.create(
    type = EventType("my", "my_event"),
    name = "我的事件",
    description = "这是一个示例事件",
    payload = mapOf("key" to "value")
)
```

### 创建状态转移

```kotlin
val transition = StateTransitionFactory.createSimpleTransition(
    fromStateId = "current_state",
    toStateId = "next_state",
    eventType = EventType("my", "my_event"),
    condition = { state, event, context -> true },
    action = { state, event, context ->
        StateTransitionResult(
            success = true,
            nextStateId = "next_state",
            updatedContext = context
        )
    }
)
```

### 运行系统

```kotlin
// 创建组件
val eventBus = EventBusFactory.create()
val stateManager = StateManagerFactory.create()
val orchestrator = StateOrchestratorFactory.create(stateManager, eventBus)

// 启动系统
orchestrator.start()

// 注册状态
stateManager.stateRegistry.registerState(myState)

// 注册状态转移
orchestrator.registerTransition(transition)

// 处理事件
val result = orchestrator.processEvent(event)

// 停止系统
orchestrator.stop()
```

## 架构优势

1. **复杂性管理**：通过状态隔离降低认知负载
2. **天然解耦**：状态间仅通过事件耦合
3. **高可观测性**：系统行为可映射到状态-事件图
4. **高可靠性**：具备语义级自愈能力
5. **自治演化**：系统可动态调整行为

## 适用场景

- 高状态复杂度系统（支付、物流、风控等）
- 事件高并发系统（物联网、监控系统等）
- 多团队协同系统
- 长生命周期系统
- 策略演化型系统

## 项目结构

```
src/
├── main/kotlin/me/ixor/sred/
│   ├── core/           # 核心接口和抽象
│   ├── event/          # 事件系统
│   ├── state/          # 状态管理
│   ├── orchestrator/   # 调度器
│   ├── examples/       # 示例实现
│   └── Main.kt         # 主程序入口
└── test/kotlin/me/ixor/sred/
    ├── core/           # 核心组件测试
    ├── event/          # 事件系统测试
    └── examples/       # 示例测试
```

## 技术栈

- **Kotlin**: 主要编程语言
- **Kotlin Coroutines**: 异步编程支持
- **Jackson**: JSON处理
- **Logback**: 日志记录
- **JUnit 5**: 单元测试
- **MockK**: 测试模拟

## 贡献指南

1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

MIT License

## 参考文献

- [论状态轮转与事件驱动结合形架构](./docs/论%20状态轮转与事件驱动结合形架构.md)
