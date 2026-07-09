package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.ChatbotConfigRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatbotConfigResponse;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ChatbotWorkflowNode;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotWorkflowNodeRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatbotConfigService {

    private final ChatbotConfigRepository chatbotConfigRepository;
    private final ChatbotWorkflowNodeRepository workflowNodeRepository;
    private final AuditLogService auditLogService;
    private final TenantService tenantService;

    public ChatbotConfigService(ChatbotConfigRepository chatbotConfigRepository,
                                ChatbotWorkflowNodeRepository workflowNodeRepository,
                                AuditLogService auditLogService,
                                TenantService tenantService) {
        this.chatbotConfigRepository = chatbotConfigRepository;
        this.workflowNodeRepository = workflowNodeRepository;
        this.auditLogService = auditLogService;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public List<ChatbotConfigResponse> list() {
        return list(AdminListQuery.empty());
        }

        @Transactional(readOnly = true)
        public List<ChatbotConfigResponse> list(AdminListQuery query) {
        return chatbotConfigRepository.findByTenantIdOrderByNameAsc(tenantService.currentTenantId()).stream()
                .map(this::toResponse)
            .filter(chatbot -> matchesListQuery(chatbot, query))
            .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),
                chatbots -> AdminListQuerySupport.apply(chatbots, query, chatbot -> true, chatbotSorters(), "name")))
            .stream()
                .toList();
    }

        private boolean matchesListQuery(ChatbotConfigResponse chatbot, AdminListQuery query) {
        return AdminListQuerySupport.containsAny(query.search(), chatbot.tenantId(), chatbot.name(),
            chatbot.description(), chatbot.enabled() ? "enabled" : "disabled")
            && AdminListQuerySupport.contains(chatbot.tenantId(), query.value("tenantId"))
            && AdminListQuerySupport.contains(chatbot.name(), query.value("name"))
            && AdminListQuerySupport.contains(chatbot.description(), query.value("description"))
            && AdminListQuerySupport.equalsBoolean(chatbot.enabled(), query.booleanValue("enabled"));
        }

        private Map<String, java.util.function.Function<ChatbotConfigResponse, ?>> chatbotSorters() {
        return Map.of(
            "tenantId", ChatbotConfigResponse::tenantId,
            "name", ChatbotConfigResponse::name,
            "description", ChatbotConfigResponse::description,
            "enabled", ChatbotConfigResponse::enabled,
            "updatedAt", ChatbotConfigResponse::updatedAt
        );
        }

    @Transactional(readOnly = true)
    public ChatbotConfigResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ChatbotConfigResponse create(ChatbotConfigRequest request) {
        ChatbotConfig chatbotConfig = new ChatbotConfig();
        chatbotConfig.setTenantId(tenantService.currentTenantId());
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
        return chatbotConfigRepository.findByTenantIdAndId(tenantService.currentTenantId(), id)
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
        ChatbotWorkflowNode startNode = new ChatbotWorkflowNode();
        startNode.setChatbot(chatbotConfig);
        startNode.setNodeKey(ChatbotWorkflowService.START_NODE_KEY);
        startNode.setName(ChatbotWorkflowService.START_NODE_KEY);
        startNode.setDescription("Built-in workflow entry node");
        startNode.setDslContent(ChatbotWorkflowService.DEFAULT_START_NODE_DSL);
        startNode.setVersion(1);
        startNode.setModel(null);
        startNode.setEnabled(true);
        startNode.setStart(true);
        startNode.setMetadata(java.util.Map.of("x", 56, "y", 64));
        workflowNodeRepository.save(startNode);
    }

    private ChatbotConfigResponse toResponse(ChatbotConfig chatbotConfig) {
        return new ChatbotConfigResponse(
                chatbotConfig.getId(),
            chatbotConfig.getTenantId(),
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
        metadata.put("tenantId", chatbotConfig.getTenantId());
        metadata.put("enabled", chatbotConfig.isEnabled());
        return metadata;
    }
}