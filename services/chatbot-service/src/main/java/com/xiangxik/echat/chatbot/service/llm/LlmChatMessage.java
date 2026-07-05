package com.xiangxik.echat.chatbot.service.llm;

import java.util.Map;

public record LlmChatMessage(
        String role,
        String content,
        Map<String, Object> metadata
) {
    public LlmChatMessage {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}