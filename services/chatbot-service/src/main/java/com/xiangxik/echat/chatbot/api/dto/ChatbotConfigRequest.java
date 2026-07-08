package com.xiangxik.echat.chatbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Chatbot configuration create/update request")
public record ChatbotConfigRequest(
        @NotBlank @Size(max = 160) String name,
        String description,
        Boolean enabled
) {
}