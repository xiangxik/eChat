package com.xiangxik.echat.chatbot.service.context;

public record ContextDslError(
        int line,
        String tag,
        String reason
) {
}