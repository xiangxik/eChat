package com.xiangxik.echat.chatbot.api.dto;

import java.time.Instant;
import java.util.Map;

public record ChatMessageResponse(
        Long id,
        Long conversationId,
        String role,
        String content,
        Integer tokenCount,
        Map<String, Object> metadata,
        Instant createdAt
) {
    public ChatMessageResponse {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}