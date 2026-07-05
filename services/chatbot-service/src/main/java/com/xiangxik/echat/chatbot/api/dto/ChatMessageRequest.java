package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ChatMessageRequest(
        @NotBlank @Size(max = 8000) String message,
        Map<String, Object> metadata
) {
    public ChatMessageRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}