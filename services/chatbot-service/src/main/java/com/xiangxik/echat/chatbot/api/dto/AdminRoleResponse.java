package com.xiangxik.echat.chatbot.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record AdminRoleResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean systemRole,
        Set<Long> permissionIds,
        List<AdminPermissionResponse> permissions,
        Instant createdAt,
        Instant updatedAt
) {
}
