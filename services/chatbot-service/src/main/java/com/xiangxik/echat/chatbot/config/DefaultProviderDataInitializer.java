package com.xiangxik.echat.chatbot.config;

import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import com.xiangxik.echat.chatbot.service.ApiKeyProtector;
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
    private final ApiKeyProtector apiKeyProtector;

    public DefaultProviderDataInitializer(ProviderConfigRepository providerConfigRepository,
                                          ApiKeyProtector apiKeyProtector) {
        this.providerConfigRepository = providerConfigRepository;
        this.apiKeyProtector = apiKeyProtector;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (ProviderSeed providerSeed : DefaultProviderCatalog.providers()) {
            providerConfigRepository.findByName(providerSeed.name())
                    .orElseGet(() -> providerConfigRepository.save(toProviderConfig(providerSeed)));
        }
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

    private String randomApiKey(String providerName) {
        return "sk-seed-" + providerName.toLowerCase().replaceAll("[^a-z0-9]+", "-")
                + UUID.randomUUID().toString().replace("-", "");
    }
}