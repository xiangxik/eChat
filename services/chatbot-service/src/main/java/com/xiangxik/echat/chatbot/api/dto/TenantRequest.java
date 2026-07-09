package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantRequest(
        @NotBlank String tenantId,
        @NotBlank String name,
        Boolean enabled
) {
}