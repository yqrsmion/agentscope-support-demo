package com.example.agentsupport;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;

/**
 * AgentScope Java 2.0 智能客服工单助手演示：
 * ReAct Agent + 本地工具 + 流式事件（观察推理与工具调用）+ (userId, sessionId) 会话隔离。
 * 运行前设置环境变量 DEEPSEEK_API_KEY。
 */
public class SupportAgentApplication {

    public static void main(String[] args) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SupportAgentTools());

        ReActAgent agent = ReActAgent.builder()
                .name("support-agent")
                .sysPrompt("""
                        你是客服工单分析助手，职责是结合本地 FAQ 与订单数据，对用户反馈进行分类、定级并给出处理建议。
                        需要查政策时调用 search_faq，需要查订单状态时调用 get_order_status，
                        需要对反馈做分类定级时调用 analyze_ticket；不要编造订单或政策内容。
                        """)
                // ModelRegistry 自动解析 deepseek: 前缀并读取 DEEPSEEK_API_KEY
                .model("deepseek:deepseek-chat")
                .toolkit(toolkit)
                .build();

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("support-demo")
                .userId("demo-user")
                .build();

        System.out.println("=== 第一轮：投诉工单分析 ===");
        runTurn(agent, ctx,
                "用户反馈：订单 10003 一直没有收到货，物流很久没更新，用户非常生气要投诉。"
                        + "请帮我分析这个工单：查一下订单状态和相关物流政策，然后给出分类、紧急度和处理建议。");

        System.out.println();
        System.out.println("=== 第二轮：同一 session 追问（验证会话记忆与上下文隔离） ===");
        runTurn(agent, ctx, "刚才这个工单最终定级是什么？建议怎么回复用户？");

        agent.close();
    }

    private static void runTurn(ReActAgent agent, RuntimeContext ctx, String userText) {
        agent.streamEvents(new UserMessage(userText), ctx)
                .doOnNext(event -> {
                    switch (event.getType()) {
                        case TEXT_BLOCK_DELTA -> System.out.print(((TextBlockDeltaEvent) event).getDelta());
                        case TOOL_CALL_START -> System.out.println(
                                "\n[tool] " + ((ToolCallStartEvent) event).getToolCallName());
                        case AGENT_END -> System.out.println("\n[agent end]");
                        default -> {
                            // 其余事件（思考块、工具结果等）可在此扩展展示
                        }
                    }
                })
                .blockLast();
        System.out.println();
    }
}
