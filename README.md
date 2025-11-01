# SRED - 状态轮转与事件驱动结合型架构

SRED（State Rotation and Event-Driven）是一个基于 Kotlin 的状态机框架，结合了状态轮转和事件驱动的设计理念，提供了强大的状态管理和持久化能力。

## 特性

- ✅ **状态管理**：完整的状态机实现，支持状态注册、转移、历史追踪
- ✅ **事件驱动**：基于事件的状态转移机制
- ✅ **持久化适配器**：支持多种数据库（SQLite、MySQL、PostgreSQL 等）的适配器模式
- ✅ **配置加载**：支持从本地文件或远程 URL 加载 JSON/XML/YAML 配置
- ✅ **注解支持**：使用 `@StateHandler` 注解声明式绑定状态处理函数
- ✅ **类型安全**：完整的类型安全的状态访问 API
- ✅ **日志框架**：集成 SLF4J/Logback 日志系统
- ✅ **资源管理**：自动资源管理和清理
- ✅ **安全性**：URL 加载时的安全验证和限制

## 快速开始

### 1. 构建项目

```bash
mvn clean compile
```

### 2. 运行示例

```bash
# 使用默认配置文件 sred.json
mvn exec:java -Dexec.mainClass="me.ixor.sred.MainKt"

# 或指定自定义配置文件
mvn exec:java -Dexec.mainClass="me.ixor.sred.MainKt" -Dexec.args="custom-config.json"

# 或使用远程 URL
mvn exec:java -Dexec.mainClass="me.ixor.sred.MainKt" -Dexec.args="https://example.com/config.json"
```

### 3. 配置文件格式

创建 `sred.json` 文件定义状态流程：

```json
{
  "name": "转账流程",
  "description": "用户转账的状态流程",
  "version": "1.0.0",
  "states": [
    {
      "id": "transfer_initiated",
      "name": "转账已发起",
      "type": "INITIAL",
      "isInitial": true
    },
    {
      "id": "validating_accounts",
      "name": "验证账户",
      "type": "NORMAL"
    },
    {
      "id": "transfer_success",
      "name": "转账成功",
      "type": "FINAL",
      "isFinal": true
    }
  ],
  "transitions": [
    {
      "from": "transfer_initiated",
      "to": "validating_accounts",
      "condition": "Success",
      "priority": 1
    }
  ]
}
```

## 使用方法

### 1. 创建状态处理器

使用 `@StateHandler` 注解声明状态处理函数：

```kotlin
class TransferStateHandlers {
    @StateHandler(
        stateId = "validating_accounts",
        description = "验证账户"
    )
    suspend fun validateAccounts(context: StateContext): StateResult {
        val fromUserId = context.getLocalState<String>("fromUserId")
        val toUserId = context.getLocalState<String>("toUserId")
        
        // 执行验证逻辑
        if (isValid(fromUserId, toUserId)) {
            return StateResult.success()
        } else {
            return StateResult.failure("账户验证失败")
        }
    }
}
```

### 2. 加载配置并创建状态机

```kotlin
import me.ixor.sred.declarative.format.FormatLoader

// 从本地文件加载
val stateFlow = FormatLoader.load("sred.json")

// 从远程 URL 加载
val stateFlow = FormatLoader.load("https://example.com/config.json")

// 绑定状态处理函数
stateFlow.bindAnnotatedFunctions(TransferStateHandlers())

// 构建状态机
val stateMachine = stateFlow.build()
```

### 3. 使用持久化适配器

```kotlin
import me.ixor.sred.persistence.PersistenceAdapterFactory

// 创建 SQLite 适配器
val persistence = PersistenceAdapterFactory.createSqliteAdapter("mydb.db")
persistence.initialize()

// 使用 use 确保资源自动关闭
persistence.use {
    // 保存上下文
    persistence.saveContext(context)
    
    // 加载上下文
    val loaded = persistence.loadContext(contextId)
    
    // 保存事件历史
    persistence.saveEvent(contextId, event)
    
    // 保存状态历史
    persistence.saveStateHistory(contextId, fromState, toState, eventId)
}
```

### 4. 创建和执行状态转移

```kotlin
// 创建初始上下文
val context = StateContextFactory.builder()
    .id("transfer_001")
    .localState("fromUserId", "userA")
    .localState("toUserId", "userB")
    .localState("amount", 200.0)
    .build()

// 启动状态机实例
val instance = stateMachine.start("transfer_001", context)

// 发送事件触发状态转移
val event = EventFactory.builder()
    .type("transfer", "process")
    .name("处理转账")
    .source("system")
    .build()

val result = instance.processEvent(event)
```

### 5. 使用调度器（Orchestrator）

```kotlin
import me.ixor.sred.orchestrator.StateOrchestratorBuilder

val orchestrator = StateOrchestratorBuilder.create()
    .withSqlitePersistence("state.db")
    .buildAndStart()

// 处理事件
val result = orchestrator.processEvent(event)
```

## 类型安全的状态访问

使用扩展函数进行类型安全的状态访问：

```kotlin
// 获取局部状态（可空）
val userId = context.getLocalState<String>("userId")

// 获取局部状态（带默认值）
val amount = context.getLocalStateOrDefault<Number>("amount", 0.0)

// 获取全局状态
val globalConfig = context.getGlobalState<Map<String, Any>>("config")

// 获取元信息
val metadata = context.getMetadata<Map<String, String>>("meta")
```

## 日志配置

项目使用 SLF4J/Logback 进行日志管理。日志配置位于 `src/main/resources/logback.xml`。

日志级别可以通过修改配置文件调整：

```xml
<logger name="me.ixor.sred" level="DEBUG" />
```

日志文件位置：
- 控制台输出：标准输出
- 所有日志：`logs/sred.log`
- 错误日志：`logs/sred-error.log`

## 持久化适配器

### SQLite（已实现）

```kotlin
val adapter = PersistenceAdapterFactory.createSqliteAdapter("db.sqlite")
```

### 其他数据库（计划实现）

- MySQL
- PostgreSQL
- H2
- MongoDB
- Redis

## 配置加载

### 从本地文件加载

```kotlin
val stateFlow = FormatLoader.loadFromFile("config.json")
```

### 从远程 URL 加载

```kotlin
val stateFlow = FormatLoader.loadFromUrl("https://example.com/config.json")
```

### 自动识别（推荐）

```kotlin
// 自动识别文件路径或 URL
val stateFlow = FormatLoader.load("config.json")  // 本地文件
val stateFlow = FormatLoader.load("https://example.com/config.json")  // 远程 URL
```

**安全特性**：
- ✅ 只允许 HTTP/HTTPS 协议
- ✅ 连接超时：10秒
- ✅ 读取超时：30秒
- ✅ 文件大小限制：10MB
- ✅ SSL 证书验证

## 错误处理

框架提供了统一的异常类型：

- `SredException`：基础异常类
- `StateException`：状态相关异常
- `EventException`：事件相关异常
- `PersistenceException`：持久化相关异常
- `ConfigurationException`：配置相关异常
- `SecurityException`：安全相关异常
- `ResourceException`：资源相关异常

## 项目结构

```
sred-kotlin/
├── src/main/kotlin/me/ixor/sred/
│   ├── core/              # 核心接口和类型
│   │   ├── Context.kt     # 状态上下文
│   │   ├── Event.kt       # 事件定义
│   │   ├── State.kt       # 状态定义
│   │   ├── Logger.kt      # 日志工具
│   │   └── SredException.kt # 异常定义
│   ├── state/             # 状态管理
│   │   ├── StateManager.kt
│   │   └── StateRegistry.kt
│   ├── event/             # 事件处理
│   │   ├── EventBus.kt
│   │   └── EventEmitter.kt
│   ├── orchestrator/      # 调度器
│   │   └── StateOrchestrator.kt
│   ├── persistence/       # 持久化适配器
│   │   ├── adapters/
│   │   │   └── SqlitePersistenceAdapter.kt
│   │   └── PersistenceAdapterFactory.kt
│   └── declarative/       # 声明式 API
│       ├── format/        # 格式加载器
│       └── annotations/   # 注解处理
├── sred.json              # 配置文件示例
└── pom.xml                # Maven 配置
```

## 依赖

- Kotlin 2.1.20
- Kotlin Coroutines 1.7.3
- Jackson (JSON/XML/YAML 处理)
- SQLite JDBC 3.44.1.0
- SLF4J/Logback (日志)
- JUnit 5 (测试)

## 开发

### 运行测试

```bash
mvn test
```

### 编译

```bash
mvn clean compile
```

### 打包

```bash
mvn clean package
```

## 许可证

本项目采用 MIT 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 文档

更多详细文档请参考：
- [架构设计文档](docs/论%20状态轮转与事件驱动结合形架构.md)

