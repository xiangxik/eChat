package com.xiangxik.echat.chatbot.api.dto;

import java.time.Instant;
import java.util.Map;

public record ChatbotWorkflowNodeResponse(
        Long id,
        String nodeKey,
        String name,
        String description,
        String dslContent,
        int version,
        Long modelId,
        boolean enabled,
        boolean start,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}