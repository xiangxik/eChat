package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.ChatbotConfigRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatbotConfigResponse;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ChatbotWorkflowNode;
import com.xiangxik.echat.chatbot.domain.model.ContextPolicy;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotWorkflowNodeRepository;
import com.xiangxik.echat.chatbot.domain.repository.ContextPolicyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatbotConfigService {

    private final ChatbotConfigRepository chatbotConfigRepository;
    private final ChatbotWorkflowNodeRepository workflowNodeRepository;
    private final ContextPolicyRepository contextPolicyRepository;
    private final AuditLogService auditLogService;

    public ChatbotConfigService(ChatbotConfigRepository chatbotConfigRepository,
                                ChatbotWorkflowNodeRepository workflowNodeRepository,
                                ContextPolicyRepository contextPolicyRepository,
                                AuditLogService auditLogService) {
        this.chatbotConfigRepository = chatbotConfigRepository;
        this.workflowNodeRepository = workflowNodeRepository;
        this.contextPolicyRepository = contextPolicyRepository;
        this.auditLogService = auditLogService;
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
        ChatbotConfig saved = chatbotConfigRepository.save(chatbotConfig);
        ensureStartNode(saved);
        auditLogService.recordAdmin("CHATBOT_CREATED", "ChatbotConfig", saved.getId(), auditMetadata(saved));
        return toResponse(saved);
    }

    @Transactional
    public ChatbotConfigResponse update(Long id, ChatbotConfigRequest request) {
        ChatbotConfig chatbotConfig = find(id);
        apply(chatbotConfig, request);
        ChatbotConfig saved = chatbotConfigRepository.save(chatbotConfig);
        auditLogService.recordAdmin("CHATBOT_UPDATED", "ChatbotConfig", saved.getId(), auditMetadata(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        ChatbotConfig chatbotConfig = find(id);
        chatbotConfigRepository.delete(chatbotConfig);
        auditLogService.recordAdmin("CHATBOT_DELETED", "ChatbotConfig", id, auditMetadata(chatbotConfig));
    }

    private ChatbotConfig find(Long id) {
        return chatbotConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ChatbotConfig", id));
    }

    private void apply(ChatbotConfig chatbotConfig, ChatbotConfigRequest request) {
        chatbotConfig.setName(request.name());
        chatbotConfig.setDescription(request.description());
        if (request.enabled() != null) {
            chatbotConfig.setEnabled(request.enabled());
        }
    }

    private void ensureStartNode(ChatbotConfig chatbotConfig) {
        if (workflowNodeRepository.findByChatbotIdAndStartTrueAndEnabledTrue(chatbotConfig.getId()).isPresent()) {
            return;
        }
        ContextPolicy defaultPolicy = contextPolicyRepository.findByName(ContextPolicyService.DEFAULT_CONTEXT_POLICY_NAME)
                .orElseThrow(() -> new IllegalStateException("Default Context Policy is not configured"));
        ChatbotWorkflowNode startNode = new ChatbotWorkflowNode();
        startNode.setChatbot(chatbotConfig);
        startNode.setNodeKey(ChatbotWorkflowService.START_NODE_KEY);
        startNode.setName(ChatbotWorkflowService.START_NODE_KEY);
        startNode.setDescription("Built-in workflow entry node");
        startNode.setContextPolicy(defaultPolicy);
        startNode.setEnabled(true);
        startNode.setStart(true);
        startNode.setMetadata(java.util.Map.of("x", 56, "y", 64));
        workflowNodeRepository.save(startNode);
    }

    private ChatbotConfigResponse toResponse(ChatbotConfig chatbotConfig) {
        return new ChatbotConfigResponse(
                chatbotConfig.getId(),
                chatbotConfig.getName(),
                chatbotConfig.getDescription(),
                chatbotConfig.isEnabled(),
                chatbotConfig.getCreatedAt(),
                chatbotConfig.getUpdatedAt()
        );
    }

    private java.util.Map<String, Object> auditMetadata(ChatbotConfig chatbotConfig) {
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("name", chatbotConfig.getName());
        metadata.put("enabled", chatbotConfig.isEnabled());
        return metadata;
    }
}