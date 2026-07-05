package com.xiangxik.echat.chatbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Chatbot configuration response")
public record ChatbotConfigResponse(
        Long id,
        String name,
        String description,
        Long contextPolicyId,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}