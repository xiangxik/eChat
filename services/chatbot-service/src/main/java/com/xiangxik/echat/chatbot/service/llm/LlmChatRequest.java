package com.xiangxik.echat.chatbot.service.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record LlmChatRequest(
        List<LlmChatMessage> messages,
        Map<String, Object> metadata,
        String requestId,
        String traceId,
        Duration timeout
) {
    public LlmChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
    }
}