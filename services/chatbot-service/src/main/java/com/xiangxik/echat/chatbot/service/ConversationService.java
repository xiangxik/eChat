package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.Conversation;
import com.xiangxik.echat.chatbot.domain.model.ConversationStatus;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ConversationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatbotConfigRepository chatbotConfigRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               ChatbotConfigRepository chatbotConfigRepository) {
        this.conversationRepository = conversationRepository;
        this.chatbotConfigRepository = chatbotConfigRepository;
    }

    @Transactional(readOnly = true)
    public Conversation get(Long id) {
        return find(id);
    }

    @Transactional(readOnly = true)
    public List<Conversation> listActiveByChatbot(Long chatbotId) {
        return conversationRepository.findByChatbotIdAndStatusOrderByUpdatedAtDesc(chatbotId, ConversationStatus.ACTIVE);
    }

    @Transactional
    public Conversation create(Long chatbotId, String userId, String anonymousSessionId, String title) {
        ChatbotConfig chatbot = chatbotConfigRepository.findById(chatbotId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatbotConfig", chatbotId));
        if (!chatbot.isEnabled()) {
            throw new IllegalArgumentException("Chatbot is disabled");
        }
        Conversation conversation = new Conversation();
        conversation.setChatbot(chatbot);
        conversation.setUserId(userId);
        conversation.setAnonymousSessionId(anonymousSessionId);
        conversation.setTitle(title);
        conversation.setStatus(ConversationStatus.ACTIVE);
        return conversationRepository.save(conversation);
    }

    @Transactional
    public Conversation updateStatus(Long id, ConversationStatus status) {
        Conversation conversation = find(id);
        conversation.setStatus(status);
        return conversationRepository.save(conversation);
    }

    @Transactional
    public void delete(Long id) {
        conversationRepository.delete(find(id));
    }

    private Conversation find(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id));
    }
}