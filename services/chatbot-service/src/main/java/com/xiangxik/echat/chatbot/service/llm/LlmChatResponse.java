package com.xiangxik.echat.chatbot.service.llm;

import java.util.Map;

public record LlmChatResponse(
        String content,
        String finishReason,
        Map<String, Object> metadata
) {
    public LlmChatResponse {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}