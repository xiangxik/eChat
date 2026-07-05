package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.domain.model.Message;
import com.xiangxik.echat.chatbot.service.context.ContextMemoryItem;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MemoryExtractionService {

    private final ShortTermMemoryCache shortTermMemoryCache;

    public MemoryExtractionService(ShortTermMemoryCache shortTermMemoryCache) {
        this.shortTermMemoryCache = shortTermMemoryCache;
    }

    public void afterTurn(Long chatbotId, Long conversationId, String userId, Message userMessage,
                          Message assistantMessage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "chat-turn-hook");
        metadata.put("chatbotId", chatbotId);
        metadata.put("conversationId", conversationId);
        metadata.put("userId", userId);
        metadata.put("userMessageId", userMessage.getId());
        metadata.put("assistantMessageId", assistantMessage.getId());
        shortTermMemoryCache.append(conversationId, new ContextMemoryItem(renderTurn(userMessage, assistantMessage),
                1.0, metadata));
    }

    private String renderTurn(Message userMessage, Message assistantMessage) {
        return "USER: " + abbreviate(userMessage.getContent()) + "\nASSISTANT: " + abbreviate(assistantMessage.getContent());
    }

    private String abbreviate(String content) {
        if (content == null || content.length() <= 600) {
            return content;
        }
        return content.substring(0, 600) + "...";
    }
}