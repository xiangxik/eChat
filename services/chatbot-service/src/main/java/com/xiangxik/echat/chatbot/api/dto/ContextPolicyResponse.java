package com.xiangxik.echat.chatbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Context policy response")
public record ContextPolicyResponse(
        Long id,
        String name,
        String description,
        String dslContent,
        int version,
        Long modelId,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}