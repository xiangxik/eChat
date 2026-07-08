package com.xiangxik.echat.chatbot.api.dto;

import java.time.Instant;
import java.util.Map;

public record ChatbotWorkflowTransitionResponse(
        Long id,
        String name,
        String fromNodeKey,
        String toNodeKey,
        int priority,
        boolean enabled,
        String conditionExpression,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}