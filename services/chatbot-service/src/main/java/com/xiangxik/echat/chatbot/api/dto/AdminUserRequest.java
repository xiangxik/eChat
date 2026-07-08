package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record AdminUserRequest(
        @NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 160) String displayName,
        @Size(max = 255) String password,
        @NotBlank @Size(max = 160) String tenantId,
        Boolean enabled,
        Set<Long> roleIds
) {
}
