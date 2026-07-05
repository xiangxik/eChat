package com.xiangxik.echat.chatbot.service.retrieval;

import java.util.Map;

public record RetrievalRequest(
        Long chatbotId,
        Long conversationId,
        String userId,
        String query,
        int topK,
        double minScore,
        Map<String, Object> metadata
) {
    public RetrievalRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}