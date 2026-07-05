package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.ContextPolicyRequest;
import com.xiangxik.echat.chatbot.api.dto.ContextPolicyDslErrorResponse;
import com.xiangxik.echat.chatbot.api.dto.ContextPolicyResponse;
import com.xiangxik.echat.chatbot.api.dto.ContextPolicyValidationResponse;
import com.xiangxik.echat.chatbot.domain.model.ContextPolicy;
import com.xiangxik.echat.chatbot.domain.repository.ContextPolicyRepository;
import com.xiangxik.echat.chatbot.service.context.ContextAssemblyRequest;
import com.xiangxik.echat.chatbot.service.context.ContextAssemblyResult;
import com.xiangxik.echat.chatbot.service.context.ContextEngine;
import com.xiangxik.echat.chatbot.service.context.ContextPolicyValidationResult;
import com.xiangxik.echat.chatbot.service.context.ContextPolicyValidator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContextPolicyService {

    private final ContextPolicyRepository contextPolicyRepository;
    private final ContextPolicyValidator contextPolicyValidator;
    private final ContextEngine contextEngine;

    public ContextPolicyService(ContextPolicyRepository contextPolicyRepository,
                                ContextPolicyValidator contextPolicyValidator,
                                ContextEngine contextEngine) {
        this.contextPolicyRepository = contextPolicyRepository;
        this.contextPolicyValidator = contextPolicyValidator;
        this.contextEngine = contextEngine;
    }

    @Transactional(readOnly = true)
    public List<ContextPolicyResponse> list() {
        return contextPolicyRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ContextPolicyResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public ContextPolicyResponse create(ContextPolicyRequest request) {
        contextPolicyValidator.validateAndParse(request.dslContent());
        ContextPolicy contextPolicy = new ContextPolicy();
        apply(contextPolicy, request);
        return toResponse(contextPolicyRepository.save(contextPolicy));
    }

    @Transactional
    public ContextPolicyResponse update(Long id, ContextPolicyRequest request) {
        contextPolicyValidator.validateAndParse(request.dslContent());
        ContextPolicy contextPolicy = find(id);
        apply(contextPolicy, request);
        return toResponse(contextPolicyRepository.save(contextPolicy));
    }

    @Transactional(readOnly = true)
    public ContextPolicyValidationResponse validate(String dslContent) {
        ContextPolicyValidationResult result = contextPolicyValidator.validate(dslContent);
        return new ContextPolicyValidationResponse(result.valid(), result.errors().stream()
                .map(error -> new ContextPolicyDslErrorResponse(error.line(), error.tag(), error.reason()))
                .toList(), result.warnings(), result.policyName(), result.maxTokens());
    }

    @Transactional(readOnly = true)
    public ContextAssemblyResult preview(Long id, ContextAssemblyRequest request) {
        ContextPolicy contextPolicy = find(id);
        return contextEngine.assemble(contextPolicyValidator.validateAndParse(contextPolicy.getDslContent()), request);
    }

    @Transactional
    public void delete(Long id) {
        contextPolicyRepository.delete(find(id));
    }

    private ContextPolicy find(Long id) {
        return contextPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ContextPolicy", id));
    }

    private void apply(ContextPolicy contextPolicy, ContextPolicyRequest request) {
        contextPolicy.setName(request.name());
        contextPolicy.setDescription(request.description());
        contextPolicy.setDslContent(request.dslContent());
        if (request.version() != null) {
            contextPolicy.setVersion(request.version());
        }
        if (request.enabled() != null) {
            contextPolicy.setEnabled(request.enabled());
        }
    }

    private ContextPolicyResponse toResponse(ContextPolicy contextPolicy) {
        return new ContextPolicyResponse(
                contextPolicy.getId(),
                contextPolicy.getName(),
                contextPolicy.getDescription(),
                contextPolicy.getDslContent(),
                contextPolicy.getVersion(),
                contextPolicy.isEnabled(),
                contextPolicy.getCreatedAt(),
                contextPolicy.getUpdatedAt()
        );
    }
}