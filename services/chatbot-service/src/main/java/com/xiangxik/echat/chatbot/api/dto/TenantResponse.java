package com.xiangxik.echat.chatbot.api.dto;

import java.time.Instant;

public record TenantResponse(
        Long id,
        String tenantId,
        String name,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}