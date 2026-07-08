package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record AdminRoleRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 160) String name,
        String description,
        Set<Long> permissionIds
) {
}
