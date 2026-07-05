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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProviderConfigService {

    private final ProviderConfigRepository providerConfigRepository;
    private final ApiKeyProtector apiKeyProtector;
    private final LlmProviderClientRegistry clientRegistry;

    public ProviderConfigService(ProviderConfigRepository providerConfigRepository, ApiKeyProtector apiKeyProtector,
                                 LlmProviderClientRegistry clientRegistry) {
        this.providerConfigRepository = providerConfigRepository;
        this.apiKeyProtector = apiKeyProtector;
        this.clientRegistry = clientRegistry;
    }

    @Transactional(readOnly = true)
    public List<ProviderConfigResponse> list() {
        return providerConfigRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProviderConfigResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ProviderConfigResponse create(ProviderConfigRequest request) {
        ProviderConfig providerConfig = new ProviderConfig();
        apply(providerConfig, request);
        return toResponse(providerConfigRepository.save(providerConfig));
    }

    @Transactional
    public ProviderConfigResponse update(Long id, ProviderConfigRequest request) {
        ProviderConfig providerConfig = find(id);
        apply(providerConfig, request);
        return toResponse(providerConfigRepository.save(providerConfig));
    }

    @Transactional
    public void delete(Long id) {
        providerConfigRepository.delete(find(id));
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
        return providerConfigRepository.findById(id)
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