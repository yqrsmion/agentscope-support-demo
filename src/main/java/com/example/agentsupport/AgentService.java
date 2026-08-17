package com.example.agentsupport;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Agent 服务：管理 ReActAgent 实例（按模型别名缓存）、会话状态持久化、
 * 模型路由 / 降级，以及本地 MCP 客户端连接。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String SYSTEM_PROMPT = """
            你是客服工单分析助手，职责是结合本地 FAQ、订单数据与知识库，对用户反馈进行分类、定级并给出处理建议。
            需要查政策时调用 search_faq，需要查订单状态时调用 get_order_status 或 MCP 工具，
            需要对反馈做分类定级时调用 analyze_ticket；不要编造订单或政策内容。
            """;

    private final JsonFileAgentStateStore stateStore;
    private final Toolkit toolkit;
    private final Map<String, ReActAgent> agents = new ConcurrentHashMap<>();

    public AgentService(
            @Value("${app.agentscope.state-dir:./data/agentscope-state}") String stateDir,
            @Value("${app.mcp.enabled:true}") boolean mcpEnabled,
            @Value("${app.mcp.script:./mcp/mcp-order-server.js}") String mcpScript) {
        this.stateStore = new JsonFileAgentStateStore(Path.of(stateDir));
        this.toolkit = new Toolkit();
        toolkit.registerTool(new SupportAgentTools());
        if (mcpEnabled) {
            try {
                McpClientWrapper mcp = McpClientBuilder.create("order-mcp")
                        .stdioTransport("node", mcpScript)
                        .buildAsync()
                        .block();
                toolkit.registerMcpClient(mcp).block();
                log.info("MCP 客户端已连接: order-mcp");
            } catch (Exception e) {
                log.warn("MCP 连接失败，跳过: {}", e.getMessage());
            }
        }
    }

    public record ParsedCommand(String modelAlias, String message) {
    }

    public record GatewayResult(String reply, String modelUsed) {
    }

    /** 解析 "/model xxx 消息内容" 形式的命令；没有命令则原样返回。 */
    public static ParsedCommand parseCommand(String message) {
        if (message != null) {
            String trimmed = message.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("/model")) {
                String rest = trimmed.substring("/model".length()).trim();
                int idx = rest.indexOf(' ');
                if (idx > 0) {
                    return new ParsedCommand(rest.substring(0, idx).trim(), rest.substring(idx + 1).trim());
                }
                return new ParsedCommand(rest.trim(), "");
            }
        }
        return new ParsedCommand(null, message);
    }

    /** 模型别名 → DeepSeek 模型 id；未知回落到默认 chat。 */
    public static String resolveModelId(String alias) {
        if (alias == null) {
            return "deepseek:deepseek-chat";
        }
        return switch (alias.trim().toLowerCase(Locale.ROOT)) {
            case "reasoner", "deepseek-reasoner" -> "deepseek:deepseek-reasoner";
            case "flash", "v4-flash", "deepseek-v4-flash" -> "deepseek:deepseek-v4-flash";
            case "pro", "v4-pro", "deepseek-v4-pro" -> "deepseek:deepseek-v4-pro";
            default -> "deepseek:deepseek-chat";
        };
    }

    public static String displayName(String alias) {
        return resolveModelId(alias).substring("deepseek:".length());
    }

    public Flux<AgentEvent> stream(String message, String userId, String sessionId, String modelAlias) {
        ReActAgent agent = agent(modelAlias);
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .build();
        return agent.streamEvents(new UserMessage(message), ctx);
    }

    public GatewayResult call(String message, String userId, String sessionId, String modelAlias) {
        StringBuilder full = new StringBuilder();
        stream(message, userId, sessionId, modelAlias)
                .doOnNext(event -> {
                    if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
                        full.append(((TextBlockDeltaEvent) event).getDelta());
                    }
                })
                .blockLast();
        return new GatewayResult(full.toString(), displayName(modelAlias));
    }

    private ReActAgent agent(String modelAlias) {
        String normalized = modelAlias == null ? "chat" : modelAlias.trim().toLowerCase(Locale.ROOT);
        return agents.computeIfAbsent(normalized, alias -> ReActAgent.builder()
                .name("support-agent-" + alias)
                .sysPrompt(SYSTEM_PROMPT)
                .model(resolveModelId(alias))
                .fallbackModel(ModelRegistry.resolve("deepseek:deepseek-chat"))
                .toolkit(toolkit)
                .stateStore(stateStore)
                // 本地演示：绕过权限引擎，避免 MCP 工具因无审批人而被静默跳过
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.BYPASS)
                        .build())
                .build());
    }

    @PreDestroy
    public void close() {
        agents.values().forEach(ReActAgent::close);
    }
}
