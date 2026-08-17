# AgentScope Java 智能客服工单助手演示工程

一个不依赖数据库和部署环境的本地 Java 工程，用"智能客服/工单分析"场景演示 AgentScope Java 2.0：

- `ReActAgent` 的推理循环：推理 -> 工具调用 -> 观察结果 -> 回复；
- `@Tool` 注解注册本地工具（查 FAQ、查订单、投诉分类定级），框架自动生成 JSON Schema 暴露给模型；
- `streamEvents()` 流式观察文本增量与工具调用全过程；
- `RuntimeContext(userId, sessionId)` 的会话隔离与上下文记忆；
- `deepseek:<model>` 通过 OpenAI 兼容栈接入 DeepSeek。

## 运行前准备

1. 安装 JDK 17 与 Maven。
2. 在当前 PowerShell 会话设置 API Key（不要写入 Git）：

```powershell
$env:DEEPSEEK_API_KEY = "你的 DeepSeek API Key"
```

3. 运行测试（无需网络）：

```powershell
mvn test
```

4. 运行演示：

```powershell
mvn exec:java
```

程序会跑两轮：第一轮对一条"物流长时间未更新、用户生气投诉"的工单做分析
（触发查订单、查 FAQ、分类定级等工具），第二轮用同一 `(userId, sessionId)` 追问结论，
验证会话记忆。流式事件会在终端实时打印 `[tool]` 标记的中间过程。

## 调用链

`UserMessage + RuntimeContext -> ReActAgent（推理循环）-> Toolkit 解析工具 Schema -> 模型决策 -> @Tool 本地方法 -> 工具结果回填 -> 继续推理 -> 最终回复`

## 与 Spring AI 的定位差异

- Spring AI：面向"把模型能力工程化嵌入现有 Spring Boot 服务"，核心是 `ChatClient`、
  Advisor 链（记忆 / RAG / 日志）、SSE、工具调用；偏集成与治理。
- AgentScope Java：面向"Agent 本身"的构建，核心是 ReAct 循环、事件流、工具系统、
  MCP、多 Agent 编排、状态存储；偏 Agent 运行时与编排。
- 两者可以共存：Spring AI 管模型接入与业务集成，AgentScope 管 Agent 编排；
  同一个服务里可以分别用两套能力解决不同层次的问题。

## 本 Demo 的边界

- 知识库是 `src/main/resources/faq.md` 本地文件，订单为模拟数据，目的是聚焦工具调用与 ReAct；
- 会话状态在当前进程内演示，重启后需要重新开始；生产可用
  `JsonFileAgentStateStore` / `RedisAgentStateStore` 等按 `(userId, sessionId)` 持久化；
- 权限、沙箱、子 Agent、技能（Skill）等工程能力属于 HarnessAgent 层，Demo 未展开。
