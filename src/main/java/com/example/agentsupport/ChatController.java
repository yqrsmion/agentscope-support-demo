package com.example.agentsupport;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final AgentService agentService;
    private final SimpleRetriever retriever;
    private final MultiAgentService multiAgentService;
    private final NativeMultiAgentService nativeMultiAgentService;

    public ChatController(AgentService agentService, SimpleRetriever retriever,
                          MultiAgentService multiAgentService,
                          NativeMultiAgentService nativeMultiAgentService) {
        this.agentService = agentService;
        this.retriever = retriever;
        this.multiAgentService = multiAgentService;
        this.nativeMultiAgentService = nativeMultiAgentService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam String message,
            @RequestParam(defaultValue = "demo-session") String conversationId,
            @RequestParam(required = false) String model) {
        String trimmed = message == null ? "" : message.trim();

        if (NativeMultiAgentService.isNativeCommand(trimmed)) {
            return multiStream(NativeMultiAgentService.stripNativeCommand(trimmed), conversationId,
                    "deepseek-chat · 原生多Agent",
                    "Harness agent_spawn 派发子 Agent 中…",
                    nativeMultiAgentService::stream);
        }
        if (MultiAgentService.isMultiCommand(trimmed)) {
            return multiStream(MultiAgentService.stripMultiCommand(trimmed), conversationId,
                    "deepseek-chat · 多Agent编排",
                    "订单/政策子 Agent 并行查询中…",
                    multiAgentService::stream);
        }

        AgentService.ParsedCommand command = AgentService.parseCommand(message);
        String realMessage = command.message();
        String modelAlias = model != null ? model : command.modelAlias();
        if (!StringUtils.hasText(realMessage)) {
            return Flux.just(
                    ServerSentEvent.builder("命令格式：/model 模型别名 问题内容").event("error").build(),
                    ServerSentEvent.builder("[DONE]").event("done").build());
        }

        List<String> hits = retriever.search(realMessage);
        String prompt = hits.isEmpty()
                ? realMessage
                : retriever.toContext(hits) + "\n\n用户问题：" + realMessage;
        int hitCount = hits.size();

        return Flux.concat(
                        Mono.just(ServerSentEvent.builder(AgentService.displayName(modelAlias)).event("model").build()),
                        hitCount > 0
                                ? Mono.just(ServerSentEvent.builder("命中" + hitCount + "个知识块").event("rag").build())
                                : Flux.empty(),
                        mapAgentEvents(agentService.stream(prompt, "demo-user", conversationId, modelAlias)))
                .concatWithValues(ServerSentEvent.builder("[DONE]").event("done").build())
                .timeout(TIMEOUT)
                .onErrorResume(error -> {
                    log.warn("stream error: {}", error.getMessage());
                    return Flux.just(
                            ServerSentEvent.builder("服务繁忙，请稍后再试（" + error.getMessage() + "）")
                                    .event("error").build(),
                            ServerSentEvent.builder("[DONE]").event("done").build());
                });
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, String>> chat(
            @RequestParam String message,
            @RequestParam(defaultValue = "demo-session") String conversationId,
            @RequestParam(required = false) String model) {
        String trimmed = message == null ? "" : message.trim();

        if (NativeMultiAgentService.isNativeCommand(trimmed)) {
            return multiChat(NativeMultiAgentService.stripNativeCommand(trimmed), conversationId,
                    "multi-native", "deepseek-chat", nativeMultiAgentService::call);
        }
        if (MultiAgentService.isMultiCommand(trimmed)) {
            return multiChat(MultiAgentService.stripMultiCommand(trimmed), conversationId,
                    "multi", "deepseek-chat", multiAgentService::call);
        }

        AgentService.ParsedCommand command = AgentService.parseCommand(message);
        String realMessage = command.message();
        String modelAlias = model != null ? model : command.modelAlias();
        if (!StringUtils.hasText(realMessage)) {
            return Mono.just(Map.of("conversationId", conversationId, "error", "命令格式：/model 模型别名 问题内容"));
        }

        List<String> hits = retriever.search(realMessage);
        String prompt = hits.isEmpty()
                ? realMessage
                : retriever.toContext(hits) + "\n\n用户问题：" + realMessage;

        return Mono.fromCallable(() -> agentService.call(prompt, "demo-user", conversationId, modelAlias))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(TIMEOUT)
                .map(result -> Map.of(
                        "conversationId", conversationId,
                        "model", result.modelUsed(),
                        "rag", hits.isEmpty() ? "off" : "命中" + hits.size() + "个知识块",
                        "reply", result.reply()))
                .onErrorResume(error -> {
                    log.warn("chat error: {}", error.getMessage());
                    return Mono.just(Map.of(
                            "conversationId", conversationId,
                            "error", "服务繁忙，请稍后再试",
                            "detail", error.getMessage()));
                });
    }

    private Flux<ServerSentEvent<String>> multiStream(
            String realMessage, String conversationId,
            String modelLabel, String statusText,
            BiFunction<String, String, Flux<AgentEvent>> streamer) {
        if (!StringUtils.hasText(realMessage)) {
            return Flux.just(
                    ServerSentEvent.builder("命令格式：/multi 或 /multi-native 客服问题").event("error").build(),
                    ServerSentEvent.builder("[DONE]").event("done").build());
        }
        return Flux.concat(
                        Mono.just(ServerSentEvent.builder(modelLabel).event("model").build()),
                        Mono.just(ServerSentEvent.builder(statusText).event("status").build()),
                        mapAgentEvents(streamer.apply(realMessage, conversationId)))
                .concatWithValues(ServerSentEvent.builder("[DONE]").event("done").build())
                .timeout(TIMEOUT)
                .onErrorResume(error -> {
                    log.warn("multi stream error: {}", error.getMessage());
                    return Flux.just(
                            ServerSentEvent.builder("多 Agent 调用失败：" + error.getMessage()).event("error").build(),
                            ServerSentEvent.builder("[DONE]").event("done").build());
                });
    }

    private Mono<Map<String, String>> multiChat(
            String realMessage, String conversationId,
            String mode, String modelLabel,
            BiFunction<String, String, String> caller) {
        if (!StringUtils.hasText(realMessage)) {
            return Mono.just(Map.of("conversationId", conversationId,
                    "error", "命令格式：/multi 或 /multi-native 客服问题"));
        }
        return Mono.fromCallable(() -> caller.apply(realMessage, conversationId))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(TIMEOUT)
                .map(reply -> Map.of(
                        "conversationId", conversationId,
                        "model", modelLabel,
                        "rag", "off",
                        "mode", mode,
                        "reply", reply))
                .onErrorResume(error -> {
                    log.warn("multi chat error: {}", error.getMessage());
                    return Mono.just(Map.of(
                            "conversationId", conversationId,
                            "error", "多 Agent 调用失败，请稍后再试",
                            "detail", error.getMessage()));
                });
    }

    private Flux<ServerSentEvent<String>> mapAgentEvents(Flux<AgentEvent> events) {
        return events.flatMap(event -> switch (event.getType()) {
            case TEXT_BLOCK_DELTA -> Mono.just(ServerSentEvent.builder(
                    ((TextBlockDeltaEvent) event).getDelta()).event("chunk").build());
            case TOOL_CALL_START -> Mono.just(ServerSentEvent.builder(
                    ((ToolCallStartEvent) event).getToolCallName()).event("tool").build());
            default -> Flux.empty();
        });
    }
}
