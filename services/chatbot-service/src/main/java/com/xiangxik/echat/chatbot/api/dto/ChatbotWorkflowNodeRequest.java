package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ChatbotWorkflowNodeRequest(
        @NotBlank @Size(max = 120) String nodeKey,
        @NotBlank @Size(max = 160) String name,
        String description,
        Long contextPolicyId,
        Boolean enabled,
        Boolean start,
        Map<String, Object> metadata
) {
}