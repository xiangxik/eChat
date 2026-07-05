package com.xiangxik.echat.chatbot.api.dto;

import com.xiangxik.echat.chatbot.service.context.TokenBudgetReport;
import java.time.Instant;
import java.util.List;

public record ChatConversationCreateResponse(
        Long id,
        Long chatbotId,
        String title,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String requestId,
        String traceId,
        ChatMessageResponse userMessage,
        ChatMessageResponse assistantMessage,
        TokenBudgetReport tokenBudgetReport,
        List<String> contextWarnings
) {
    public ChatConversationCreateResponse {
        contextWarnings = contextWarnings == null ? List.of() : List.copyOf(contextWarnings);
    }

    public static ChatConversationCreateResponse withoutInitialMessage(ChatConversationResponse conversation,
                                                                       String requestId, String traceId) {
        return new ChatConversationCreateResponse(conversation.id(), conversation.chatbotId(), conversation.title(),
                conversation.status(), conversation.createdAt(), conversation.updatedAt(), requestId, traceId,
                null, null, null, List.of());
    }

    public static ChatConversationCreateResponse withInitialMessage(ChatRuntimeResponse runtimeResponse) {
        ChatConversationResponse conversation = runtimeResponse.conversation();
        return new ChatConversationCreateResponse(conversation.id(), conversation.chatbotId(), conversation.title(),
                conversation.status(), conversation.createdAt(), conversation.updatedAt(), runtimeResponse.requestId(),
                runtimeResponse.traceId(), runtimeResponse.userMessage(), runtimeResponse.assistantMessage(),
                runtimeResponse.tokenBudgetReport(), runtimeResponse.contextWarnings());
    }
}