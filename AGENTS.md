# 协作说明（供 Codex 阅读）

本工程是学习演示工程。用户的目的是快速掌握 AgentScope Java 的工作原理与核心组件，
视角偏向企业级应用，可随学习进度逐步扩展代码。

## 已完成功能（当前版本）

- ReAct 推理循环 + `streamEvents()` 实时事件流；
- 本地工具（FAQ / 订单 / 工单分析）与 MCP 本地工具（Node stdio）；
- 会话状态持久化（JsonFileAgentStateStore，重启不丢）；
- 模型路由 `chat / reasoner / flash / pro` 与 fallbackModel 降级；
- 简化版 RAG（本地文档切块检索注入）；
- Spring Boot 网页界面 + SSE；10 个单元测试全绿。

## 协作约定（务必遵守）

- Git 提交信息一律使用中文，并写明本次改了什么。
- 提交前先执行 `git status` / `git diff`，只暂存本次任务相关文件；不要用 `git add -A`
  全量添加，避免把用户在 IDEA 里手动改的文件（或误输入的字符）一起提交。
- 用户可能在 IDEA 中误输入字符，提交前留意工作区差异。
- 模型 API Key 一律通过环境变量 DEEPSEEK_API_KEY 提供，不写入代码、文档或 Git。
- 工程内不得包含任何个人信息或真实业务数据。
- 交付时结合代码讲解工作原理、组件与调用链，不必逐行展开；文档需包含可复现的验证方式。
