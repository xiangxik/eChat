package com.xiangxik.echat.chatbot.service.llm;

import java.util.Map;

public record LlmStreamEvent(
        String token,
        String messageDelta,
        boolean done,
        String finishReason,
        Map<String, Object> metadata
) {
    public LlmStreamEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static LlmStreamEvent token(String token, String messageDelta) {
        return new LlmStreamEvent(token, messageDelta, false, null, Map.of());
    }

    public static LlmStreamEvent done(String finishReason) {
        return new LlmStreamEvent(null, null, true, finishReason, Map.of());
    }
}