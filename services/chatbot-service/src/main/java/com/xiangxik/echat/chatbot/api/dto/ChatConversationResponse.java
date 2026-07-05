package com.xiangxik.echat.chatbot.api.dto;

import java.time.Instant;

public record ChatConversationResponse(
        Long id,
        Long chatbotId,
        String title,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}