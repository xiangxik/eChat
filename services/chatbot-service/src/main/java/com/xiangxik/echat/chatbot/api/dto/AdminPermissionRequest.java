package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminPermissionRequest(
        @NotBlank @Size(max = 128) String code,
        @NotBlank @Size(max = 160) String name,
        String description
) {
}
