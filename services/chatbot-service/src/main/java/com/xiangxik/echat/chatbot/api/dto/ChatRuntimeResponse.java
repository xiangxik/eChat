package com.xiangxik.echat.chatbot.api.dto;

import com.xiangxik.echat.chatbot.service.context.TokenBudgetReport;
import java.util.List;

public record ChatRuntimeResponse(
        String requestId,
        String traceId,
        ChatConversationResponse conversation,
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage,
        TokenBudgetReport tokenBudgetReport,
        List<String> contextWarnings
) {
    public ChatRuntimeResponse {
        contextWarnings = contextWarnings == null ? List.of() : List.copyOf(contextWarnings);
    }
}