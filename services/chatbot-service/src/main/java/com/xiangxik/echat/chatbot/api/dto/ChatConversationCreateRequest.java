package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ChatConversationCreateRequest(
        @NotNull Long chatbotId,
    @Size(max = 8000) String message,
        @Size(max = 128) String userId,
        @Size(max = 128) String anonymousSessionId,
        @Size(max = 240) String title,
        Map<String, Object> metadata
) {
    public ChatConversationCreateRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}