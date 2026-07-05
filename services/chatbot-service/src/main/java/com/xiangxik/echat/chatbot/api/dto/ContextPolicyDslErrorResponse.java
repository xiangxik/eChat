package com.xiangxik.echat.chatbot.api.dto;

public record ContextPolicyDslErrorResponse(
        int line,
        String tag,
        String reason
) {
}