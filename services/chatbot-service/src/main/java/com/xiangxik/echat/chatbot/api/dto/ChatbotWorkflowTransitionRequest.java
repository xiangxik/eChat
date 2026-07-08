package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ChatbotWorkflowTransitionRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 120) String fromNodeKey,
        @NotBlank @Size(max = 120) String toNodeKey,
        @Min(0) Integer priority,
        Boolean enabled,
        @NotBlank String conditionExpression,
        Map<String, Object> metadata
) {
}