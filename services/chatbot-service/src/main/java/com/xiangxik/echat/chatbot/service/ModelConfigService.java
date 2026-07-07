package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.ModelConfigRequest;
import com.xiangxik.echat.chatbot.api.dto.ModelConfigResponse;
import com.xiangxik.echat.chatbot.api.dto.ModelGenerationTestResponse;
import com.xiangxik.echat.chatbot.api.dto.ModelOptionResponse;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.repository.ModelConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClient;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClientRegistry;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderTestResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;
    private final ProviderConfigRepository providerConfigRepository;
    private final ProviderConfigService providerConfigService;
    private final ProviderModelDiscoveryService providerModelDiscoveryService;
    private final LlmProviderClientRegistry clientRegistry;
    private final AuditLogService auditLogService;

    public ModelConfigService(ModelConfigRepository modelConfigRepository,
                              ProviderConfigRepository providerConfigRepository,
                              ProviderConfigService providerConfigService,
                              ProviderModelDiscoveryService providerModelDiscoveryService,
                              LlmProviderClientRegistry clientRegistry,
                              AuditLogService auditLogService) {
        this.modelConfigRepository = modelConfigRepository;
        this.providerConfigRepository = providerConfigRepository;
        this.providerConfigService = providerConfigService;
        this.providerModelDiscoveryService = providerModelDiscoveryService;
        this.clientRegistry = clientRegistry;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ModelConfigResponse> list() {
        return modelConfigRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ModelConfigResponse get(Long id) {
        return toResponse(find(id));
    }

        @Transactional(readOnly = true)
        public List<ModelOptionResponse> listOptions(Long providerId) {
        ProviderConfig provider = providerConfigRepository.findById(providerId)
            .orElseThrow(() -> new ResourceNotFoundException("ProviderConfig", providerId));
            return providerModelDiscoveryService.listModels(provider, providerConfigService.decryptedApiKey(provider));
        }

    @Transactional
    public ModelConfigResponse create(ModelConfigRequest request) {
        ModelConfig modelConfig = new ModelConfig();
        apply(modelConfig, request);
        ModelConfig saved = modelConfigRepository.save(modelConfig);
        auditLogService.recordAdmin("MODEL_CREATED", "ModelConfig", saved.getId(), auditMetadata(saved));
        return toResponse(saved);
    }

    @Transactional
    public ModelConfigResponse update(Long id, ModelConfigRequest request) {
        ModelConfig modelConfig = find(id);
        apply(modelConfig, request);
        ModelConfig saved = modelConfigRepository.save(modelConfig);
        auditLogService.recordAdmin("MODEL_UPDATED", "ModelConfig", saved.getId(), auditMetadata(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        ModelConfig modelConfig = find(id);
        modelConfigRepository.delete(modelConfig);
        auditLogService.recordAdmin("MODEL_DELETED", "ModelConfig", id, auditMetadata(modelConfig));
    }

    @Transactional(readOnly = true)
    public ModelGenerationTestResponse testGeneration(Long id) {
        ModelConfig modelConfig = find(id);
        ProviderConfig providerConfig = modelConfig.getProvider();
        LlmProviderClient client = clientRegistry.getClient(providerConfig.getType());
        LlmProviderTestResult result = client.testGeneration(
                providerConfig,
                modelConfig,
                providerConfigService.decryptedApiKey(providerConfig)
        );
        return new ModelGenerationTestResponse(
                result.success(),
                providerConfig.getType().name(),
                modelConfig.getModelName(),
                result.message(),
                result.statusCode(),
                result.sampleText()
        );
    }

    private ModelConfig find(Long id) {
        return modelConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ModelConfig", id));
    }

    private void apply(ModelConfig modelConfig, ModelConfigRequest request) {
        ProviderConfig provider = providerConfigRepository.findById(request.providerId())
                .orElseThrow(() -> new ResourceNotFoundException("ProviderConfig", request.providerId()));
        modelConfig.setProvider(provider);
        modelConfig.setDisplayName(request.displayName());
        modelConfig.setModelName(request.modelName());
        modelConfig.setModelType(request.modelType());
        modelConfig.setMaxContextTokens(request.maxContextTokens());
        modelConfig.setDefaultTemperature(request.defaultTemperature());
        modelConfig.setDefaultTopP(request.defaultTopP());
        if (request.supportsStreaming() != null) {
            modelConfig.setSupportsStreaming(request.supportsStreaming());
        }
        if (request.enabled() != null) {
            modelConfig.setEnabled(request.enabled());
        }
        modelConfig.setMetadata(request.metadata());
    }

    private ModelConfigResponse toResponse(ModelConfig modelConfig) {
        return new ModelConfigResponse(
                modelConfig.getId(),
                modelConfig.getProvider().getId(),
                modelConfig.getProvider().getName(),
                modelConfig.getDisplayName(),
                modelConfig.getModelName(),
                modelConfig.getModelType(),
                modelConfig.getMaxContextTokens(),
                modelConfig.getDefaultTemperature(),
                modelConfig.getDefaultTopP(),
                modelConfig.isSupportsStreaming(),
                modelConfig.isEnabled(),
                modelConfig.getMetadata()
        );
    }

    private java.util.Map<String, Object> auditMetadata(ModelConfig modelConfig) {
        return java.util.Map.of(
                "providerId", modelConfig.getProvider().getId(),
                "displayName", modelConfig.getDisplayName(),
                "modelName", modelConfig.getModelName(),
                "modelType", modelConfig.getModelType().name(),
                "enabled", modelConfig.isEnabled()
        );
    }
}