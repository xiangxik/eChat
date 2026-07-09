package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.domain.model.Conversation;
import com.xiangxik.echat.chatbot.domain.model.Message;
import com.xiangxik.echat.chatbot.domain.model.MessageRole;
import com.xiangxik.echat.chatbot.domain.repository.ConversationRepository;
import com.xiangxik.echat.chatbot.domain.repository.MessageRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    public MessageService(MessageRepository messageRepository, ConversationRepository conversationRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    @Transactional(readOnly = true)
    public List<Message> listByConversation(String tenantId, Long conversationId) {
        return messageRepository.findByConversationTenantIdAndConversationIdOrderByCreatedAtAsc(tenantId, conversationId);
    }

    @Transactional
    public Message create(String tenantId, Long conversationId, MessageRole role, String content, Integer tokenCount,
                          Map<String, Object> metadata) {
        Conversation conversation = conversationRepository.findByTenantIdAndIdWithChatbotAndWorkflowNode(tenantId, conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setTokenCount(tokenCount);
        message.setMetadata(metadata);
        return messageRepository.save(message);
    }

    @Transactional
    public void delete(Long id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message", id));
        messageRepository.delete(message);
    }
}