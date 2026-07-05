package com.xiangxik.echat.chatbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Context policy create/update request")
public record ContextPolicyRequest(
        @NotBlank @Size(max = 160) String name,
        String description,
        @NotBlank String dslContent,
        @Positive Integer version,
        Boolean enabled
) {
}