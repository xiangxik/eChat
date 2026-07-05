package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.ChatbotConfigRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatbotConfigResponse;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ContextPolicyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatbotConfigService {

    private final ChatbotConfigRepository chatbotConfigRepository;
    private final ContextPolicyRepository contextPolicyRepository;

    public ChatbotConfigService(ChatbotConfigRepository chatbotConfigRepository,
                                ContextPolicyRepository contextPolicyRepository) {
        this.chatbotConfigRepository = chatbotConfigRepository;
        this.contextPolicyRepository = contextPolicyRepository;
    }

    @Transactional(readOnly = true)
    public List<ChatbotConfigResponse> list() {
        return chatbotConfigRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ChatbotConfigResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ChatbotConfigResponse create(ChatbotConfigRequest request) {
        ChatbotConfig chatbotConfig = new ChatbotConfig();
        apply(chatbotConfig, request);
        return toResponse(chatbotConfigRepository.save(chatbotConfig));
    }

    @Transactional
    public ChatbotConfigResponse update(Long id, ChatbotConfigRequest request) {
        ChatbotConfig chatbotConfig = find(id);
        apply(chatbotConfig, request);
        return toResponse(chatbotConfigRepository.save(chatbotConfig));
    }

    @Transactional
    public void delete(Long id) {
        chatbotConfigRepository.delete(find(id));
    }

    private ChatbotConfig find(Long id) {
        return chatbotConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ChatbotConfig", id));
    }

    private void apply(ChatbotConfig chatbotConfig, ChatbotConfigRequest request) {
        chatbotConfig.setName(request.name());
        chatbotConfig.setDescription(request.description());
        chatbotConfig.setContextPolicy(request.contextPolicyId() == null ? null : contextPolicyRepository.findById(request.contextPolicyId())
                .orElseThrow(() -> new ResourceNotFoundException("ContextPolicy", request.contextPolicyId())));
        if (request.enabled() != null) {
            chatbotConfig.setEnabled(request.enabled());
        }
    }

    private ChatbotConfigResponse toResponse(ChatbotConfig chatbotConfig) {
        Long contextPolicyId = chatbotConfig.getContextPolicy() == null ? null : chatbotConfig.getContextPolicy().getId();
        return new ChatbotConfigResponse(
                chatbotConfig.getId(),
                chatbotConfig.getName(),
                chatbotConfig.getDescription(),
                contextPolicyId,
                chatbotConfig.isEnabled(),
                chatbotConfig.getCreatedAt(),
                chatbotConfig.getUpdatedAt()
        );
    }
}