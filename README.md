## SRED - 状态轮转与事件驱动结合型架构 案例

SRED（State Rotation & Event-Driven）是一个以“状态为中心、事件驱动推进”的工作流/编排引擎。它把复杂业务拆解成显式状态与事件，支持长时间暂停、可持久化恢复的流程执行，适合实现注册、审批、转账等多阶段业务。

### 主要能力
- 状态流与转移：声明式定义状态、条件和转移，内置顺序/分支/并行模式
- 事件驱动：以事件推进状态，事件负载会与上下文合并参与决策
- 长时间暂停：可在某些状态自动暂停，支持服务重启后恢复执行
- 持久化：上下文、事件、状态历史可落库（内置 SQLite 适配）
- 注解绑定：`@StateHandler` 将业务函数绑定至状态
- 类型安全访问：便捷的上下文数据读取 API
- 健康检查与日志：Logback 输出、健康检测

---

## 目录结构与各包职责

- `api/`
  - `Application.kt`：Spring Boot 启动入口。
  - `controllers/ApiController.kt`：REST API（转账与注册），约定返回码：处理中 202，成功 200，明确失败 400。
  - `models/`：API 请求/响应模型，如 `RegistrationRequest/Response`。
  - `services/`：API 编排服务，如 `RegistrationApiService` 维护引擎、异步执行与恢复。
- `core/`
  - 基础抽象与内核类型：`Event`, `StateContext`, `StateContextImpl`, `EventFactory`, 日志、异常等。
  - 负责上下文、事件的标准化与类型安全访问。
- `declarative/`
  - 声明式状态流引擎：`StateFlow`, `StateMachine` 及注解绑定。支持顺序/条件/并行/Join 等执行模式。
  - 关键点：在顺序模式中，事件 payload 会在调用状态函数前合并到 `localState`，便于状态函数读取输入。
- `event/`
  - 事件总线与分发（内部使用），统计、过滤等。
- `orchestrator/`
  - 智能编排器，提供“直到完成”的推进、暂停检测、错误处理等高层策略。
- `persistence/`
  - 持久化适配层，默认 SQLite。负责上下文/事件/状态历史的存取，支持从持久化“恢复实例”。
- `policy/`
  - 策略加载与版本管理（对高级场景开放）。
- `health/`
  - 健康检查实现（事件总线/持久化/状态管理等）。
- `examples/`
  - 示例业务：转账、分支、多工作流、注册等；注册示例包含领域模型、服务与视图。
- `state/`
  - 低层状态机管理的扩展实现。
- `SRED.kt`
  - 门面：统一创建 `SREDEngine`，加载配置、绑定处理器、启动编排器等。

---

## 核心原理与执行流程

1) 配置加载
- 通过 `SRED.fromConfig(configPath, dbPath, handlers)` 载入 JSON 配置，构建 `StateFlow → StateMachine`，绑定注解函数，创建 `SREDEngine`。
- 如有 `dbPath`，初始化持久化适配器（如 SQLite）并记录上下文/事件/状态历史。

2) 实例启动
- `engine.start(instanceId, initialData)` 创建初始上下文（含初始状态），持久化上下文并启动实例。

3) 推进执行（两种方式）
- 自动推进：`engine.runUntilComplete(...)` 循环触发事件处理，直至到达终态或遇到“需暂停”的状态。
- 外部控制：通过 `WorkflowExecutor.executeAsync` 后台推进；当遇到配置了 `pauseOnEnter=true` 的状态（如等待验证）自动暂停，等待外部事件唤醒。

4) 事件处理
- `engine.process(instanceId, eventType, eventName, payload)` 会：
  - 构建 `Event` 并持久化
  - 在 `StateMachine.processEvent` 中，按执行模式选择处理函数
  - 顺序模式下：将 `event.payload` 合并到上下文 `localState` 后调用状态函数
  - 根据 `StateResult` 与转移定义选择下一个状态，更新上下文并持久化

5) 暂停与恢复
- 当状态定义 `pauseOnEnter=true` 时，进入该状态会写入暂停 metadata，并在 `WorkflowExecutor` 中登记为暂停实例。
- 外部通过 API 触发事件（如提交验证码）→ 继续执行 → 若状态发生转移，清除暂停标记并继续推进，直至终态。

6) 终态与返回码建议
- 成功终态（名称含 `success` 或配置 FINAL）：返回 200
- 明确失败（名称含 `failed` 或 ERROR）：返回 400
- 中间处理（非终态/非失败）：返回 202（Accepted）

---

## 配置模型（以注册为例）

文件：`registration.json`
- 状态：
  - `registration_initiated`（INITIAL）
  - `validating` → 校验用户输入
  - `sending_email` → 发送验证码
  - `waiting_verification`（pauseOnEnter=true）→ 暂停等待用户输入
  - `verifying_code` → 校验验证码
  - `activating` → 激活用户
  - `registration_success`（FINAL）
  - `registration_failed`（ERROR）
- 转移条件：
  - 典型为 `Success/Failure`，由 `StateResult` 决定。

---

## 生命周期与时序

- 启动期：
  - `Application.kt` 启动 Spring Boot；
  - `RegistrationApiService` 初始化时创建引擎、注册到执行器、尝试从持久化恢复暂停实例。
- 请求期：
  - 发起注册：创建实例并后台推进到 `waiting_verification`，API 返回 202 与 `instanceId`。
  - 提交验证码：将验证码写入上下文并触发 `verify` 事件，后台推进至 `registration_success`，API 在处理中返回 202，成功时 200。
- 关闭期：
  - 执行器与引擎关闭：清理任务，释放资源。

---

## 用户注册案例：端到端实现

涉及文件：
- `examples/registration/RegistrationStates.kt`：状态常量
- `examples/registration/RegistrationDomain.kt`：领域模型 `RegistrationUser`
- `examples/registration/RegistrationService.kt`：状态处理器（含 `@StateHandler`）
- `examples/registration/RegistrationFixtures.kt`：内存上下文（用户表、验证码表）
- `examples/registration/RegistrationView.kt`：示例输出（日志）
- `api/services/RegistrationApiService.kt`：注册流程编排（引擎 + 执行器）
- `api/controllers/ApiController.kt`：API 暴露
- `registration.json`：注册流程配置

状态处理器摘录（关键逻辑）：
- 验证输入（`validating`）：校验用户名、邮箱格式、密码强度与重名；通过则写入 `validated=true`
- 发送验证码（`sending_email`）：生成 6 位验证码写入 `verificationCodes[email]`，并把 `verificationCode` 写回上下文
- 验证码校验（`verifying_code`）：比对 `inputCode` 与 `verificationCodes[email]`，成功写 `verified=true`
- 激活账户（`activating`）：将用户状态置为 `ACTIVATED`，生成 `userId`

执行编排（API 服务的关键点）：
- 启动注册：`executeRegistration`
  - `engine.start` → `executor.executeAsync(autoProcess=false)`，依配置暂停在 `waiting_verification`
  - 返回 202，携带 `instanceId`
  - 开发期日志会打印 `验证码已发送到 email: code`
- 提交验证码：`submitVerificationCode`
  - 先把验证码写入上下文的 `inputCode`
  - 触发 `verify` 事件（`eventType=verify, payload={"inputCode": code}`）
  - 若进入中间态（如 `verifying_code/activating`）返回 202；到达 `registration_success` 返回 200；错误返回 400

API 一览（`/api/v1`）：
- POST `/registration` 启动注册，返回 202 和 `instanceId`
- POST `/registration/{instanceId}/verify` 提交验证码，中间态 202，成功 200，错误 400
- GET `/registration/{instanceId}` 查询状态（随时可用）
- GET `/health` 健康检查

---

## 本地运行与调试

启动服务：
```bash
mvn spring-boot:run
```

注册用户：
```bash
curl -X POST http://localhost:8080/api/v1/registration \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","email":"test@example.com","password":"password123"}'
```

查看验证码：
```bash
tail -f logs/sred.log | grep 验证码
```

提交验证码：
```bash
curl -X POST http://localhost:8080/api/v1/registration/{instanceId}/verify \
  -H "Content-Type: application/json" \
  -d '{"verificationCode":"<日志中的验证码>"}'
```

查询状态：
```bash
curl http://localhost:8080/api/v1/registration/{instanceId}
```

HTTP 返回码约定：
- 202 Accepted：流程已创建/处理中（未到终态）
- 200 OK：到达成功终态
- 400 Bad Request：明确失败或参数问题
- 500 Internal Server Error：服务器错误

---

## 设计要点与最佳实践

- 状态命名：建议以业务名作前缀，例如 `registration_*`，终态以 `_success/_failed` 结尾便于识别
- 事件命名：`eventType.eventAction` 或简单 `eventType`，例如 `verify`/`process`
- 数据传递：统一通过 `localState` 传递业务数据；外部事件 payload 会自动合并到 `localState`
- 暂停/恢复：把“等待用户输入/外部系统回调”的状态配置为 `pauseOnEnter=true`
- 幂等性：状态函数应尽量保证幂等；必要时检查 `context.metadata` 或数据库
- 可观测性：启用 DEBUG 日志，结合状态历史/事件历史定位问题

---

## 依赖与开发

- Kotlin、Coroutines、Spring Boot、Jackson、SQLite JDBC、SLF4J/Logback、JUnit 5

常用命令：
```bash
mvn clean compile
mvn test
mvn spring-boot:run
mvn clean package
```

---

## 许可证与贡献

- 许可证：MIT
- 欢迎提交 Issue / PR

更多背景与设计动机，可阅读 `docs/论 状态轮转与事件驱动结合形架构.md`。

