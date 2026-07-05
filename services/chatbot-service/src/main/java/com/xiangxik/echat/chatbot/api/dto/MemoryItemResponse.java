package com.xiangxik.echat.chatbot.api.dto;

import com.xiangxik.echat.chatbot.domain.model.MemoryScope;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Memory item response")
public record MemoryItemResponse(
        Long id,
        Long chatbotId,
        String userId,
        MemoryScope scope,
        String content,
        Integer embeddingDimension,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
}