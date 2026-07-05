package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.MemoryItem;
import com.xiangxik.echat.chatbot.domain.model.MemoryScope;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.MemoryItemRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryItemService {

    private final MemoryItemRepository memoryItemRepository;
    private final ChatbotConfigRepository chatbotConfigRepository;

    public MemoryItemService(MemoryItemRepository memoryItemRepository, ChatbotConfigRepository chatbotConfigRepository) {
        this.memoryItemRepository = memoryItemRepository;
        this.chatbotConfigRepository = chatbotConfigRepository;
    }

    @Transactional(readOnly = true)
    public List<MemoryItem> listByScope(Long chatbotId, MemoryScope scope) {
        return memoryItemRepository.findByChatbotIdAndScopeOrderByUpdatedAtDesc(chatbotId, scope);
    }

    @Transactional
    public MemoryItem create(Long chatbotId, String userId, MemoryScope scope, String content, float[] embedding,
                             Map<String, Object> metadata) {
        ChatbotConfig chatbot = chatbotConfigRepository.findById(chatbotId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatbotConfig", chatbotId));
        MemoryItem memoryItem = new MemoryItem();
        memoryItem.setChatbot(chatbot);
        memoryItem.setUserId(userId);
        memoryItem.setScope(scope);
        memoryItem.setContent(content);
        memoryItem.setEmbedding(embedding);
        memoryItem.setMetadata(metadata);
        return memoryItemRepository.save(memoryItem);
    }

    @Transactional
    public void delete(Long id) {
        MemoryItem memoryItem = memoryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MemoryItem", id));
        memoryItemRepository.delete(memoryItem);
    }
}