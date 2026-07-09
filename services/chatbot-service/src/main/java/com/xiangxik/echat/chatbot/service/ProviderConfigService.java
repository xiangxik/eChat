package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.ProviderConfigRequest;
import com.xiangxik.echat.chatbot.api.dto.ProviderConfigResponse;
import com.xiangxik.echat.chatbot.api.dto.ProviderConnectionTestResponse;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClient;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClientRegistry;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderTestResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProviderConfigService {

    private final ProviderConfigRepository providerConfigRepository;
    private final ApiKeyProtector apiKeyProtector;
    private final LlmProviderClientRegistry clientRegistry;
    private final AuditLogService auditLogService;
    private final TenantService tenantService;

    public ProviderConfigService(ProviderConfigRepository providerConfigRepository, ApiKeyProtector apiKeyProtector,
                                 LlmProviderClientRegistry clientRegistry, AuditLogService auditLogService,
                                 TenantService tenantService) {
        this.providerConfigRepository = providerConfigRepository;
        this.apiKeyProtector = apiKeyProtector;
        this.clientRegistry = clientRegistry;
        this.auditLogService = auditLogService;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public List<ProviderConfigResponse> list() {
        return list(AdminListQuery.empty());
        }

        @Transactional(readOnly = true)
        public List<ProviderConfigResponse> list(AdminListQuery query) {
        return providerConfigRepository.findByTenantIdOrderByNameAsc(tenantService.currentTenantId()).stream()
                .map(this::toResponse)
            .filter(provider -> matchesListQuery(provider, query))
            .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),
                providers -> AdminListQuerySupport.apply(providers, query, provider -> true, providerSorters(), "name")))
            .stream()
                .toList();
    }

        private boolean matchesListQuery(ProviderConfigResponse provider, AdminListQuery query) {
        return AdminListQuerySupport.containsAny(query.search(), provider.name(), provider.tenantId(), provider.type(),
            provider.baseUrl(), provider.enabled() ? "enabled" : "disabled", provider.hasApiKey() ? "stored" : "not set")
            && AdminListQuerySupport.contains(provider.name(), query.value("name"))
            && AdminListQuerySupport.contains(provider.tenantId(), query.value("tenantId"))
            && AdminListQuerySupport.contains(provider.baseUrl(), query.value("baseUrl"))
            && AdminListQuerySupport.equalsText(provider.type(), query.value("type"))
            && AdminListQuerySupport.equalsBoolean(provider.enabled(), query.booleanValue("enabled"))
            && AdminListQuerySupport.equalsBoolean(provider.hasApiKey(), query.booleanValue("hasApiKey"));
        }

        private Map<String, java.util.function.Function<ProviderConfigResponse, ?>> providerSorters() {
        return Map.of(
            "tenantId", ProviderConfigResponse::tenantId,
            "name", ProviderConfigResponse::name,
            "type", ProviderConfigResponse::type,
            "baseUrl", ProviderConfigResponse::baseUrl,
            "hasApiKey", ProviderConfigResponse::hasApiKey,
            "enabled", ProviderConfigResponse::enabled,
            "updatedAt", ProviderConfigResponse::updatedAt
        );
        }

    @Transactional(readOnly = true)
    public ProviderConfigResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ProviderConfigResponse create(ProviderConfigRequest request) {
        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setTenantId(tenantService.currentTenantId());
        apply(providerConfig, request);
        ProviderConfig saved = providerConfigRepository.save(providerConfig);
        auditLogService.recordAdmin("PROVIDER_CREATED", "ProviderConfig", saved.getId(), auditMetadata(saved));
        return toResponse(saved);
    }

    @Transactional
    public ProviderConfigResponse update(Long id, ProviderConfigRequest request) {
        ProviderConfig providerConfig = find(id);
        apply(providerConfig, request);
        ProviderConfig saved = providerConfigRepository.save(providerConfig);
        auditLogService.recordAdmin("PROVIDER_UPDATED", "ProviderConfig", saved.getId(), auditMetadata(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        ProviderConfig providerConfig = find(id);
        providerConfigRepository.delete(providerConfig);
        auditLogService.recordAdmin("PROVIDER_DELETED", "ProviderConfig", id,
                java.util.Map.of("name", providerConfig.getName(), "type", providerConfig.getType().name()));
    }

    @Transactional(readOnly = true)
    public ProviderConnectionTestResponse testConnection(Long id) {
        ProviderConfig providerConfig = find(id);
        LlmProviderClient client = clientRegistry.getClient(providerConfig.getType());
        LlmProviderTestResult result = client.testConnection(providerConfig, decryptedApiKey(providerConfig));
        return new ProviderConnectionTestResponse(
                result.success(),
                providerConfig.getType().name(),
                result.message(),
                result.statusCode()
        );
    }

    private ProviderConfig find(Long id) {
        return providerConfigRepository.findByTenantIdAndId(tenantService.currentTenantId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("ProviderConfig", id));
    }

    private void apply(ProviderConfig providerConfig, ProviderConfigRequest request) {
        providerConfig.setName(request.name());
        providerConfig.setType(request.type());
        providerConfig.setBaseUrl(request.baseUrl());
        providerConfig.setApiKeySecretRef(request.apiKeySecretRef());
        if (request.enabled() != null) {
            providerConfig.setEnabled(request.enabled());
        }
        if (StringUtils.hasText(request.apiKey())) {
            providerConfig.setEncryptedApiKey(apiKeyProtector.encrypt(request.apiKey()));
        }
    }

    private ProviderConfigResponse toResponse(ProviderConfig providerConfig) {
        boolean hasApiKey = StringUtils.hasText(providerConfig.getEncryptedApiKey())
                || StringUtils.hasText(providerConfig.getApiKeySecretRef());
        return new ProviderConfigResponse(
                providerConfig.getId(),
            providerConfig.getTenantId(),
                providerConfig.getName(),
                providerConfig.getType(),
                providerConfig.getBaseUrl(),
                providerConfig.getApiKeySecretRef(),
                hasApiKey,
                providerConfig.isEnabled(),
                providerConfig.getCreatedAt(),
                providerConfig.getUpdatedAt()
        );
    }

    private java.util.Map<String, Object> auditMetadata(ProviderConfig providerConfig) {
        return java.util.Map.of(
                "name", providerConfig.getName(),
                "tenantId", providerConfig.getTenantId(),
                "type", providerConfig.getType().name(),
                "enabled", providerConfig.isEnabled(),
                "hasApiKey", StringUtils.hasText(providerConfig.getEncryptedApiKey())
                        || StringUtils.hasText(providerConfig.getApiKeySecretRef())
        );
    }

    String decryptedApiKey(ProviderConfig providerConfig) {
        if (StringUtils.hasText(providerConfig.getEncryptedApiKey())) {
            return apiKeyProtector.decrypt(providerConfig.getEncryptedApiKey());
        }
        if (StringUtils.hasText(providerConfig.getApiKeySecretRef())) {
            throw new IllegalArgumentException("API key secret references are not resolvable in this stage");
        }
        return null;
    }
}