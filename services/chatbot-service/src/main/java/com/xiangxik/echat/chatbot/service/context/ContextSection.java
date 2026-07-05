package com.xiangxik.echat.chatbot.service.context;

public record ContextSection(
        String name,
        String role,
        String content,
        int estimatedTokens,
        boolean included
) {
}