package com.xiangxik.echat.chatbot.api.dto;

import com.xiangxik.echat.chatbot.service.context.TokenBudgetReport;
import java.util.Map;

public record ChatStreamEventResponse(
        String type,
        String requestId,
        String traceId,
        Long conversationId,
        Long messageId,
        String token,
        String messageDelta,
        ChatMessageResponse message,
        TokenBudgetReport tokenBudgetReport,
        Map<String, Object> metadata
) {
    public ChatStreamEventResponse {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}