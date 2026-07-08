package com.xiangxik.echat.chatbot.config;

import com.xiangxik.echat.chatbot.domain.model.ContextPolicy;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.repository.ContextPolicyRepository;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import com.xiangxik.echat.chatbot.service.ApiKeyProtector;
import com.xiangxik.echat.chatbot.service.ContextPolicyService;
import com.xiangxik.echat.chatbot.service.DefaultProviderCatalog;
import com.xiangxik.echat.chatbot.service.DefaultProviderCatalog.ProviderSeed;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DefaultProviderDataInitializer implements ApplicationRunner {

    private final ProviderConfigRepository providerConfigRepository;
    private final ContextPolicyRepository contextPolicyRepository;
    private final ApiKeyProtector apiKeyProtector;

    public DefaultProviderDataInitializer(ProviderConfigRepository providerConfigRepository,
                                          ContextPolicyRepository contextPolicyRepository,
                                          ApiKeyProtector apiKeyProtector) {
        this.providerConfigRepository = providerConfigRepository;
        this.contextPolicyRepository = contextPolicyRepository;
        this.apiKeyProtector = apiKeyProtector;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (ProviderSeed providerSeed : DefaultProviderCatalog.providers()) {
            providerConfigRepository.findByName(providerSeed.name())
                    .orElseGet(() -> providerConfigRepository.save(toProviderConfig(providerSeed)));
        }
        contextPolicyRepository.findByName(ContextPolicyService.DEFAULT_CONTEXT_POLICY_NAME)
            .ifPresentOrElse(this::markDefaultContextPolicy,
                () -> contextPolicyRepository.save(toDefaultContextPolicy()));
    }

    private ProviderConfig toProviderConfig(ProviderSeed seed) {
        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setName(seed.name());
        providerConfig.setType(seed.type());
        providerConfig.setBaseUrl(seed.baseUrl());
        providerConfig.setEncryptedApiKey(apiKeyProtector.encrypt(randomApiKey(seed.name())));
        providerConfig.setEnabled(false);
        return providerConfig;
    }

        private ContextPolicy toDefaultContextPolicy() {
                ContextPolicy contextPolicy = new ContextPolicy();
                contextPolicy.setName(ContextPolicyService.DEFAULT_CONTEXT_POLICY_NAME);
                repairDefaultContextPolicy(contextPolicy);
                return contextPolicy;
        }

        private void repairDefaultContextPolicy(ContextPolicy contextPolicy) {
                contextPolicy.setDescription("System managed welcome policy for the built-in Start workflow node.");
                contextPolicy.setDslContent("""
                                <contextPolicy name="default-welcome" maxTokens="512">
                                    <system priority="100">Reply with exactly: Welcome! How can I help you today?</system>
                                    <output>
                                        <section name="system" />
                                    </output>
                                </contextPolicy>
                                """);
                contextPolicy.setVersion(1);
                contextPolicy.setModel(null);
                contextPolicy.setEnabled(true);
                contextPolicy.setSystemManaged(true);
        }

            private void markDefaultContextPolicy(ContextPolicy contextPolicy) {
                contextPolicy.setEnabled(true);
                contextPolicy.setSystemManaged(true);
            }

    private String randomApiKey(String providerName) {
        return "sk-seed-" + providerName.toLowerCase().replaceAll("[^a-z0-9]+", "-")
                + UUID.randomUUID().toString().replace("-", "");
    }
}