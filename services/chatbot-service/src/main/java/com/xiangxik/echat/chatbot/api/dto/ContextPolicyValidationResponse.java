package com.xiangxik.echat.chatbot.api.dto;

import java.util.List;

public record ContextPolicyValidationResponse(
        boolean valid,
        List<ContextPolicyDslErrorResponse> errors,
        List<String> warnings,
        String policyName,
        int maxTokens
) {
}