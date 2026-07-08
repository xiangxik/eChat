package com.xiangxik.echat.chatbot.api.dto;

import java.util.List;

public record ChatbotWorkflowValidationResponse(
        boolean valid,
        List<String> errors,
        List<String> warnings
) {
}