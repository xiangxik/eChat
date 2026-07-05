package com.xiangxik.echat.chatbot.service.context;

import java.util.List;

public record ContextPolicyValidationResult(
        boolean valid,
        List<ContextDslError> errors,
        List<String> warnings,
        String policyName,
        int maxTokens
) {
}