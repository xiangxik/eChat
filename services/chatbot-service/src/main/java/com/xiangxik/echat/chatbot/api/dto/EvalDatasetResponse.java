package com.xiangxik.echat.chatbot.api.dto;

import java.time.Instant;

public record EvalDatasetResponse(
        Long id,
        String name,
        String description,
        Long chatbotId,
        Instant createdAt,
        Instant updatedAt
) {
}
