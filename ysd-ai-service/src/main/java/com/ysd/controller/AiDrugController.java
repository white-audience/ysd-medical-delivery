package com.ysd.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ysd.controller.dto.AiChatRequest;
import com.ysd.controller.vo.AiAnswerVO;
import com.ysd.controller.vo.RetrievedChunkVO;
import com.ysd.result.Result;
import com.ysd.support.ConversationKeys;
import com.ysd.tool.DrugQueryTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/ai/drug")
public class AiDrugController {

    private static final String EVENT_MESSAGE = "message";
    private static final String EVENT_DONE = "done";
    private static final String EVENT_ERROR = "error";
    private static final String IDENTITY_HEADER = "X-Identity-Id";
    private static final String CONVERSATION_HEADER = "X-Conversation-Id";
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_CONVERSATION_ID_LENGTH = 64;
    private static final Pattern CONVERSATION_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(120);

    private final ChatClient chatClient;
    private final DrugQueryTool drugQueryTool;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final ObjectMapper objectMapper;
    private final VectorStore vectorStore;
    private final int ragTopK;
    private final double ragThreshold;

    public AiDrugController(ChatClient.Builder builder, DrugQueryTool drugQueryTool,
                            VectorStore vectorStore, ObjectMapper objectMapper,
                            @Value("${rag.search.top-k:4}") int ragTopK,
                            @Value("${rag.search.similarity-threshold:0.5}") double ragThreshold) {
        this.drugQueryTool = drugQueryTool;
        this.objectMapper = objectMapper;
        this.vectorStore = vectorStore;
        this.ragTopK = ragTopK;
        this.ragThreshold = ragThreshold;

        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(ragTopK)
                        .similarityThreshold(ragThreshold)
                        .build())
                .build();

        this.chatClient = builder
                .defaultSystem("你是药速达的智能助手。请遵守以下规则：\n" +
                        "1. 涉及药品事实（适应症、禁忌、厂家、处方药属性）时，必须以工具（queryDrugInfo）返回的数据为准。\n" +
                        "2. 涉及平台规则、技术架构、项目说明时，以检索到的文档内容为准。\n" +
                        "3. 如果工具和文档都没有提供相关信息，请如实回答不知道，绝对不得编造。\n" +
                        "4. 遇到处方药，必须明确提示用户需上传处方后才能购买。\n" +
                        "5. 不要使用任何餐饮行业词汇。")
                .defaultAdvisors(memoryAdvisor, qaAdvisor)
                .build();
    }

    /**
     * 阻塞式问答：保留给不需要流式的客户端；conversationId 随响应体回传。
     * userId 只信任网关注入的 X-Identity-Id，客户端伪造的同名头已在网关被剥离。
     */
    @PostMapping("/chat")
    @SentinelResource(value = "aiQuery", blockHandler = "handleChatBlock")
    public ResponseEntity<Result<AiAnswerVO>> chat(@RequestBody(required = false) AiChatRequest request,
                                                   @RequestHeader(IDENTITY_HEADER) String userId) {
        String message = request == null ? null : normalizeMessage(request.message());
        if (message == null || message.isEmpty()) {
            return ResponseEntity.badRequest().body(Result.error("请输入您想咨询的药品名称或症状。"));
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return ResponseEntity.badRequest().body(Result.error("问题过长，请精简到 2000 字以内。"));
        }

        String conversationId = resolveConversationId(request);
        String memoryKey = memoryKey(userId, conversationId);
        log.info("AI 阻塞问答，userId={}, conversationId={}", userId, conversationId);

        String answer = chatClient.prompt()
                .user(message)
                .tools(drugQueryTool)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey))
                .call()
                .content();

        return ResponseEntity.ok()
                .header(CONVERSATION_HEADER, conversationId)
                .body(Result.success(new AiAnswerVO(conversationId, answer)));
    }

    /**
     * SSE 流式问答：返回 Flux<ServerSentEvent>，由 Spring MVC 异步写出（不占用 Tomcat 请求线程）。
     * 事件约定：message = 增量文本；done = {"conversationId": "..."}；error = 错误提示。
     * conversationId 同时放在响应头 X-Conversation-Id，便于客户端在收到首块前即可拿到会话标识。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SentinelResource(value = "aiStream", blockHandler = "handleChatStreamBlock")
    public ResponseEntity<Flux<ServerSentEvent<String>>> chatStream(
            @RequestBody(required = false) AiChatRequest request,
            @RequestHeader(IDENTITY_HEADER) String userId) {

        String conversationId = resolveConversationId(request);
        String message = request == null ? null : normalizeMessage(request.message());

        if (message == null || message.isEmpty()) {
            return sseResponse(conversationId, Flux.just(errorEvent("请输入您想咨询的药品名称或症状。")));
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return sseResponse(conversationId, Flux.just(errorEvent("问题过长，请精简到 2000 字以内。")));
        }

        String memoryKey = memoryKey(userId, conversationId);
        log.info("AI 流式问答开始，userId={}, conversationId={}", userId, conversationId);

        Flux<ServerSentEvent<String>> body = chatClient.prompt()
                .user(message)
                .tools(drugQueryTool)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryKey))
                .stream()
                .content()
                // 模型客户端回调在 reactor-netty 线程上，切到 boundedElastic 再写 Servlet 响应，
                // 避免慢客户端拖住模型连接的事件循环。
                .publishOn(Schedulers.boundedElastic())
                .map(chunk -> ServerSentEvent.<String>builder(chunk).event(EVENT_MESSAGE).build())
                .concatWith(Mono.fromSupplier(() -> doneEvent(conversationId)))
                .timeout(STREAM_TIMEOUT)
                .onErrorResume(e -> {
                    log.error("AI 流式问答失败，userId={}, conversationId={}", userId, conversationId, e);
                    return Mono.just(errorEvent("AI 服务繁忙，请稍后再试。"));
                })
                .doOnCancel(() -> log.info("客户端断开流式连接，conversationId={}", conversationId));

        return sseResponse(conversationId, body);
    }

    /**
     * 旧版 GET 接口：保留兼容，不参与会话隔离，建议客户端迁移到 /chat 或 /chat/stream。
     */
    @GetMapping("/query")
    @SentinelResource(value = "aiQuery", blockHandler = "handleBlock")
    public Result<String> query(@RequestParam("question") String question,
                                @RequestParam(value = "conversationId", required = false) String conversationId) {
        if (question == null || question.trim().isEmpty()) {
            return Result.success("请输入您想查询的药品名称或症状。");
        }
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
            log.info("未传 conversationId，生成新会话：{}", conversationId);
        }
        final String cid = conversationId;

        String answer = chatClient.prompt()
                .user(question)
                .tools(drugQueryTool)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                .call()
                .content();
        return Result.success(answer);
    }

    /**
     * 检索诊断接口：仅返回 RAG 召回的片段与相似度，不调用大模型。
     * 用于离线评测和调参，topK / threshold 不传时使用 application.yml 中的默认值。
     */
    @GetMapping("/debug/retrieve")
    @SentinelResource(value = "aiQuery", blockHandler = "handleRetrieveBlock")
    public Result<List<RetrievedChunkVO>> retrieve(@RequestParam("q") String q,
                                                  @RequestParam(value = "topK", required = false) Integer topK,
                                                  @RequestParam(value = "threshold", required = false) Double threshold) {
        if (q == null || q.isBlank()) {
            return Result.error("请输入检索问题");
        }
        int k = topK == null ? ragTopK : Math.max(1, Math.min(topK, 20));
        double t = threshold == null ? ragThreshold : Math.max(0D, Math.min(threshold, 1D));

        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(q)
                .topK(k)
                .similarityThreshold(t)
                .build());
        List<RetrievedChunkVO> chunks = documents == null ? List.of() : documents.stream()
                .map(d -> {
                    String text = d.getText();
                    String source = String.valueOf(d.getMetadata().getOrDefault("source", ""));
                    if (source.isBlank() && text.startsWith("【文档：")) {
                        int end = text.indexOf('】');
                        source = end > 0 ? text.substring(4, end) : "";
                    }
                    return new RetrievedChunkVO(d.getId(), d.getScore(), source, text, d.getMetadata());
                })
                .toList();
        return Result.success(chunks);
    }

    public Result<List<RetrievedChunkVO>> handleRetrieveBlock(String q, Integer topK, Double threshold,
                                                              BlockException ex) {
        log.warn("RAG 检索诊断接口被限流，q={}", q);
        return Result.error("请求过于频繁，请稍后重试");
    }

    public ResponseEntity<Result<AiAnswerVO>> handleChatBlock(AiChatRequest request, String userId,
                                                              BlockException ex) {
        log.warn("AI 问答接口被限流，userId={}", userId);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Result.error("请求过于频繁，请稍后重试"));
    }

    public ResponseEntity<Flux<ServerSentEvent<String>>> handleChatStreamBlock(AiChatRequest request,
                                                                              String userId,
                                                                              BlockException ex) {
        log.warn("AI 流式接口被限流，userId={}", userId);
        return sseResponse(resolveConversationId(request),
                Flux.just(errorEvent("请求过于频繁，请稍后重试")));
    }

    public Result<String> handleBlock(String question, String conversationId, BlockException ex) {
        log.warn("AI 问答接口被限流，question={}", question);
        return Result.error("请求过于频繁，请稍后重试");
    }

    private ResponseEntity<Flux<ServerSentEvent<String>>> sseResponse(String conversationId,
                                                                      Flux<ServerSentEvent<String>> body) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONNECTION, "keep-alive")
                // 关闭 Nginx 缓冲，确保逐块下发
                .header("X-Accel-Buffering", "no")
                .header(CONVERSATION_HEADER, conversationId)
                .body(body);
    }

    private ServerSentEvent<String> doneEvent(String conversationId) {
        return ServerSentEvent.<String>builder(toJson(Map.of("conversationId", conversationId)))
                .event(EVENT_DONE)
                .build();
    }

    private ServerSentEvent<String> errorEvent(String message) {
        return ServerSentEvent.<String>builder(message).event(EVENT_ERROR).build();
    }

    private String toJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 仅包含 UUID / 已校验的会话 ID，理论上不会失败
            return "{\"conversationId\":\"\"}";
        }
    }

    private String normalizeMessage(String message) {
        return message == null ? null : message.trim();
    }

    /**
     * 客户端回传的 conversationId 只做格式校验；非法值直接换新，避免任意字符串进入记忆 Map。
     */
    private String resolveConversationId(AiChatRequest request) {
        String conversationId = request == null ? null : request.conversationId();
        if (conversationId == null || conversationId.isBlank()
                || conversationId.length() > MAX_CONVERSATION_ID_LENGTH
                || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
            return UUID.randomUUID().toString();
        }
        return conversationId;
    }

    /**
     * 记忆键按用户隔离：即使两个用户拿到同一个 conversationId，也不会读到对方的对话历史。
     */
    private String memoryKey(String userId, String conversationId) {
        return ConversationKeys.memoryKey(userId, conversationId);
    }
}
