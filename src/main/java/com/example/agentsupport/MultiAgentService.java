package com.example.agentsupport;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import jakarta.annotation.PreDestroy;
import java.util.Locale;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 多 Agent 方案 B：手写编排。
 * 两个子 ReActAgent（订单/政策，各只挂自己的工具）并行执行，
 * 汇总 Agent 拿到两份结果后综合分析。零新依赖，行为完全可控。
 */
@Service
public class MultiAgentService {

    private static final String SYS_ORDER =
            "你是订单查询子 agent。从用户问题中识别订单号，只调用 get_order_status 查询订单状态并如实返回，"
                    + "不要分析、不要编造。用中文简洁回复。";
    private static final String SYS_POLICY =
            "你是客服政策子 agent。调用 search_faq 查询与用户问题相关的政策（物流/退换货/售后/发票等），"
                    + "如实返回检索结果。用中文简洁回复。";
    private static final String SYS_SUMMARY =
            "你是客服工单分析助手。根据给定的用户问题、订单子 Agent 结果与政策子 Agent 结果，"
                    + "综合分析并给出处理建议。用中文输出。";

    private final ReActAgent orderAgent;
    private final ReActAgent policyAgent;
    private final ReActAgent summarizer;

    public MultiAgentService() {
        Toolkit orderToolkit = new Toolkit();
        orderToolkit.registerTool(new OrderTool());
        Toolkit policyToolkit = new Toolkit();
        policyToolkit.registerTool(new PolicyTool());

        orderAgent = build("order-agent", SYS_ORDER, orderToolkit);
        policyAgent = build("policy-agent", SYS_POLICY, policyToolkit);
        summarizer = build("multi-summarizer", SYS_SUMMARY, new Toolkit());
    }

    public static boolean isMultiCommand(String message) {
        return message != null && message.trim().toLowerCase(Locale.ROOT).startsWith("/multi ");
    }

    public static String stripMultiCommand(String message) {
        return message.trim().substring("/multi".length()).trim();
    }

    public Flux<AgentEvent> stream(String question, String conversationId) {
        String sid = conversationId + "-" + System.nanoTime();
        return Mono.fromCallable(() -> {
            String order = collect(orderAgent,
                    "识别用户问题中的订单号并查询订单状态。用户问题：" + question, sid + "-order");
            String policy = collect(policyAgent,
                    "查询与用户问题相关的客服政策。用户问题：" + question, sid + "-policy");
            return "【用户问题】" + question
                    + "\n【订单子 Agent 结果】" + order
                    + "\n【政策子 Agent 结果】" + policy
                    + "\n\n请综合分析并给出处理建议。";
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(prompt -> summarizer.streamEvents(new UserMessage(prompt), ctx(sid + "-summary")));
    }

    public String call(String question, String conversationId) {
        StringBuilder sb = new StringBuilder();
        stream(question, conversationId)
                .doOnNext(e -> {
                    if (e.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                        sb.append(((TextBlockDeltaEvent) e).getDelta());
                    }
                })
                .blockLast();
        return sb.toString();
    }

    private static ReActAgent build(String name, String sysPrompt, Toolkit toolkit) {
        return ReActAgent.builder()
                .name(name)
                .sysPrompt(sysPrompt)
                .model("deepseek:deepseek-chat")
                .toolkit(toolkit)
                .permissionContext(PermissionContextState.builder().mode(PermissionMode.BYPASS).build())
                .build();
    }

    private static RuntimeContext ctx(String sessionId) {
        return RuntimeContext.builder().sessionId(sessionId).userId("demo-user").build();
    }

    private static String collect(ReActAgent agent, String msg, String sessionId) {
        StringBuilder sb = new StringBuilder();
        agent.streamEvents(new UserMessage(msg), ctx(sessionId))
                .doOnNext(e -> {
                    if (e.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                        sb.append(((TextBlockDeltaEvent) e).getDelta());
                    }
                })
                .blockLast();
        return sb.toString();
    }

    @PreDestroy
    public void close() {
        orderAgent.close();
        policyAgent.close();
        summarizer.close();
    }
}
