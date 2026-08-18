package com.example.agentsupport;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.subagent.AgentSpecLoader;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 多 Agent 方案 A：AgentScope 原生 HarnessAgent。
 * 通过 workspace/subagents 下的 md spec 声明子 agent，父 agent 用 agent_spawn 派发，
 * 框架负责子 agent workspace、状态、任务等基建。
 */
@Service
public class NativeMultiAgentService {

    private static final Logger log = LoggerFactory.getLogger(NativeMultiAgentService.class);

    private final HarnessAgent agent;

    public NativeMultiAgentService() throws IOException {
        Path workspace = Path.of("./data/harness/workspace");
        Path subagents = workspace.resolve("subagents");
        Files.createDirectories(subagents);
        Files.writeString(subagents.resolve("order-checker.md"), """
                ---
                description: 订单状态查询专家。当需要核实具体订单状态时使用。
                tools: [get_order_status]
                ---
                你是订单状态查询子 agent，只调用 get_order_status 查询订单并如实返回结果。请用中文简洁回复。
                """);
        Files.writeString(subagents.resolve("policy-expert.md"), """
                ---
                description: 客服政策查询专家。当需要查询退换货、物流、发票、售后等政策时使用。
                tools: [search_faq]
                ---
                你是客服政策子 agent，只调用 search_faq 查询政策并如实返回结果。请用中文简洁回复。
                """);

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SupportAgentTools());

        agent = HarnessAgent.builder()
                .name("support-orchestrator")
                .sysPrompt("你是客服工单分析主管。遇到工单时，必须依次派发两个专用子 agent："
                        + "先派 order-checker 核实订单状态，再派 policy-expert 查询物流/退换货政策；"
                        + "禁止使用 general-purpose。拿到两个子 agent 的结果后，再综合分析给出结论。")
                .model("deepseek:deepseek-chat")
                .toolkit(toolkit)
                .workspace(workspace)
                .subagents(AgentSpecLoader.loadFromDirectory(subagents, workspace))
                .build();
        log.info("原生多 Agent（HarnessAgent）已初始化，workspace={}", workspace);
    }

    public static boolean isNativeCommand(String message) {
        return message != null && message.trim().toLowerCase(Locale.ROOT).startsWith("/multi-native ");
    }

    public static String stripNativeCommand(String message) {
        return message.trim().substring("/multi-native".length()).trim();
    }

    public Flux<AgentEvent> stream(String question, String conversationId) {
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(conversationId + "-native-" + System.nanoTime())
                .userId("demo-user")
                .build();
        return agent.streamEvents(new UserMessage(question), ctx);
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

    @PreDestroy
    public void close() {
        agent.close();
    }
}
