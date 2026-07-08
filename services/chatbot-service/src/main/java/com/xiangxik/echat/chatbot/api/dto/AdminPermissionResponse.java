package com.xiangxik.echat.chatbot.api.dto;

import java.time.Instant;

public record AdminPermissionResponse(
        Long id,
        String code,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
