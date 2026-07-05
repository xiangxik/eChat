package com.xiangxik.echat.chatbot.service.context;

import java.util.LinkedHashMap;
import java.util.Map;

public record ContextMemoryItem(
        String content,
        double score,
        Map<String, Object> metadata
) {
    public ContextMemoryItem {
        metadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);
    }
}