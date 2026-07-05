package com.xiangxik.echat.chatbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Context policy DSL validation request")
public record ContextPolicyValidateRequest(
        @NotBlank String dslContent
) {
}