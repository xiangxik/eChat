package com.xiangxik.echat.chatbot.service.context;

import java.util.List;
import java.util.Map;

public record ContextAssemblyRequest(
        Long chatbotId,
        Long conversationId,
        String userId,
        String latestUserMessage,
        Map<String, Object> metadata,
        List<ContextMessage> conversation,
        List<ContextMemoryItem> shortTermMemory,
        List<ContextMemoryItem> longTermMemory,
        Map<String, Object> userProfile,
        List<ContextMemoryItem> retrievalResults,
        List<ContextMemoryItem> toolResults,
        Map<String, Object> runtime
) {
    public ContextAssemblyRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        conversation = conversation == null ? List.of() : List.copyOf(conversation);
        shortTermMemory = shortTermMemory == null ? List.of() : List.copyOf(shortTermMemory);
        longTermMemory = longTermMemory == null ? List.of() : List.copyOf(longTermMemory);
        userProfile = userProfile == null ? Map.of() : Map.copyOf(userProfile);
        retrievalResults = retrievalResults == null ? List.of() : List.copyOf(retrievalResults);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        runtime = runtime == null ? Map.of() : Map.copyOf(runtime);
    }
}