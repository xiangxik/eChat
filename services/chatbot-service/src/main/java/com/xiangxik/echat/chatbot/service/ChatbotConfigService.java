package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.ChatbotConfigRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatbotConfigResponse;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ContextPolicyRepository;
import com.xiangxik.echat.chatbot.domain.repository.ModelConfigRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatbotConfigService {

    private final ChatbotConfigRepository chatbotConfigRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final ContextPolicyRepository contextPolicyRepository;

    public ChatbotConfigService(ChatbotConfigRepository chatbotConfigRepository,
                                ModelConfigRepository modelConfigRepository,
                                ContextPolicyRepository contextPolicyRepository) {
        this.chatbotConfigRepository = chatbotConfigRepository;
        this.modelConfigRepository = modelConfigRepository;
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
        chatbotConfig.setDefaultModel(request.defaultModelId() == null ? null : modelConfigRepository.findById(request.defaultModelId())
                .orElseThrow(() -> new ResourceNotFoundException("ModelConfig", request.defaultModelId())));
        chatbotConfig.setContextPolicy(request.contextPolicyId() == null ? null : contextPolicyRepository.findById(request.contextPolicyId())
                .orElseThrow(() -> new ResourceNotFoundException("ContextPolicy", request.contextPolicyId())));
        if (request.enabled() != null) {
            chatbotConfig.setEnabled(request.enabled());
        }
    }

    private ChatbotConfigResponse toResponse(ChatbotConfig chatbotConfig) {
        Long defaultModelId = chatbotConfig.getDefaultModel() == null ? null : chatbotConfig.getDefaultModel().getId();
        Long contextPolicyId = chatbotConfig.getContextPolicy() == null ? null : chatbotConfig.getContextPolicy().getId();
        return new ChatbotConfigResponse(
                chatbotConfig.getId(),
                chatbotConfig.getName(),
                chatbotConfig.getDescription(),
                defaultModelId,
                contextPolicyId,
                chatbotConfig.isEnabled(),
                chatbotConfig.getCreatedAt(),
                chatbotConfig.getUpdatedAt()
        );
    }
}