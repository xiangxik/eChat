package com.xiangxik.echat.chatbot.service.context;

import java.util.List;

public record ContextMemoryBundle(
        List<ContextMemoryItem> shortTermMemory,
        List<ContextMemoryItem> longTermMemory,
        List<ContextMemoryItem> retrievalResults
) {
    public ContextMemoryBundle {
        shortTermMemory = shortTermMemory == null ? List.of() : List.copyOf(shortTermMemory);
        longTermMemory = longTermMemory == null ? List.of() : List.copyOf(longTermMemory);
        retrievalResults = retrievalResults == null ? List.of() : List.copyOf(retrievalResults);
    }
}