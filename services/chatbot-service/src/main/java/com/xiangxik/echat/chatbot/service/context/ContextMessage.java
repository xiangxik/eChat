package com.xiangxik.echat.chatbot.service.context;

import java.util.LinkedHashMap;
import java.util.Map;

public record ContextMessage(
        String role,
        String content,
        Map<String, Object> metadata
) {
    public ContextMessage {
        metadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
    }
}