package com.docmind.gateway.system.controller;

import com.docmind.gateway.common.security.CurrentUser;
import com.docmind.gateway.system.dto.ChatRequest;
import com.docmind.gateway.system.dto.PythonChatRequest;
import com.docmind.gateway.system.entity.Citation;
import com.docmind.gateway.system.entity.Conversation;
import com.docmind.gateway.system.service.ConversationService;
import com.docmind.gateway.system.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final WebClient pythonServiceClient;
    private final MessageService messageService;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;


    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest req) {

        StringBuilder fullAnswer = new StringBuilder();
        List<Citation> citations = new ArrayList<>();
        String conversationId = req.getConversationId();
        boolean isNewConversation = (conversationId == null || conversationId.isBlank());
        if (isNewConversation) {
            // 新建一个会话，标题先留空或用问题内容截断生成
            Conversation conversation = conversationService.create(req.getQuestion());
            conversationId = conversation.getId();
        }
        final String finalConversationId = conversationId;   // 包装成一个不会再变的变量
        // 组装历史记录
        List<PythonChatRequest.HistoryItem> history = messageService
                .listRecent(finalConversationId, 10)          // 最近 10 条，约 5 轮
                .stream()
                .filter(m -> m.getContent() != null && !m.getContent().isBlank())
                .map(m -> new PythonChatRequest.HistoryItem(m.getRole(), m.getContent()))
                .toList();

        PythonChatRequest pyReq = new PythonChatRequest();
        pyReq.setQuestion(req.getQuestion());
        pyReq.setHistory(history);
        pyReq.setDocIds(req.getDocIds());   // 新增
        pyReq.setUserId(CurrentUser.require());
        // 先存用户消息，不用等生成完
        messageService.saveUserMessage(conversationId, req.getQuestion());
        // 2. 调用 Python，拿到原始 SSE 文本流
        Flux<ServerSentEvent<String>> pythonStream = pythonServiceClient.post()
                .uri("/internal/chat/stream")
                .bodyValue(pyReq)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})  // Python 用 StreamingResponse 输出的是纯文本块
                .doOnNext(event -> {
                    String eventType = event.event();
                    String data = event.data();
                    switch (eventType) {
                        case "token" -> fullAnswer.append(extractText(data));
                        case "citations" -> citations.addAll(parseCitations(data));
                    }
                })
                .doOnComplete(() -> {
                    // 3. 流结束，落库
                    if (!fullAnswer.isEmpty()) {
                        messageService.saveAssistantMessage(
                                finalConversationId, fullAnswer.toString(), citations
                        );
                    }
                })
                .doOnError(e -> {
                    // Python 服务异常，记录日志（下一步会讲怎么给前端返回统一格式的 error）
//                    log.error("Python 服务调用失败", e);
                })
                .onErrorResume(e -> {
                    log.error("调用 Python 服务失败", e);
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data("{\"message\":\"服务暂时不可用，请稍后重试\"}")
                                    .build()
                    );
                });

        if (isNewConversation) {
            // 新会话时，在流最前面插入一条 conversation_created 事件
            ServerSentEvent<String> conversationEvent = ServerSentEvent.<String>builder()
                    .event("conversation_created")
                    .data("{\"conversationId\":\"" + finalConversationId + "\"}")
                    .build();
            return Flux.concat(Flux.just(conversationEvent), pythonStream);
        }
        // 4. 原样返回给前端，前端拿到的和直连 Python 时格式完全一致
        return pythonStream;
    }

    private String extractText(String jsonData) {
        try {
            JsonNode node = objectMapper.readTree(jsonData);
            JsonNode textNode = node.get("text");
            return textNode != null ? textNode.asText() : "";
        } catch (Exception e) {
            log.warn("解析 token 事件失败，原始数据：{}", jsonData, e);
            return "";
        }
    }

    private List<Citation> parseCitations(String jsonData) {
        try {
            return objectMapper.readValue(jsonData, new TypeReference<List<Citation>>() {});
        } catch (Exception e) {
            log.warn("解析 citations 事件失败，原始数据：{}", jsonData, e);
            return List.of();
        }
    }
}