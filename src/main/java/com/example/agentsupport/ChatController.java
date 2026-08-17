package com.example.agentsupport;

import java.time.Duration;
import java.util.List;
import java.util.Map;
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

    public ChatController(AgentService agentService, SimpleRetriever retriever) {
        this.agentService = agentService;
        this.retriever = retriever;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam String message,
            @RequestParam(defaultValue = "demo-session") String conversationId,
            @RequestParam(required = false) String model) {
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
                        agentService.stream(prompt, "demo-user", conversationId, modelAlias)
                                .flatMap(event -> switch (event.getType()) {
                                    case TEXT_BLOCK_DELTA -> Mono.just(ServerSentEvent.builder(
                                            ((io.agentscope.core.event.TextBlockDeltaEvent) event).getDelta())
                                            .event("chunk").build());
                                    case TOOL_CALL_START -> Mono.just(ServerSentEvent.builder(
                                            ((io.agentscope.core.event.ToolCallStartEvent) event).getToolCallName())
                                            .event("tool").build());
                                    default -> Flux.empty();
                                }))
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
}
