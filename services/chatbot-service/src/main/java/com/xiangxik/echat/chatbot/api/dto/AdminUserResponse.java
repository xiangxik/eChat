package com.xiangxik.echat.chatbot.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record AdminUserResponse(
        Long id,
        String username,
        String displayName,
        String tenantId,
        boolean enabled,
        boolean systemUser,
        Set<Long> roleIds,
        List<AdminRoleResponse> roles,
        Instant createdAt,
        Instant updatedAt
) {
}
