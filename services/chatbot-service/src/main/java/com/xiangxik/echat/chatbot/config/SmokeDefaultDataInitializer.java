package com.xiangxik.echat.chatbot.config;

import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import com.xiangxik.echat.chatbot.service.ApiKeyProtector;
import com.xiangxik.echat.chatbot.service.DefaultProviderCatalog;
import com.xiangxik.echat.chatbot.service.DefaultProviderCatalog.ProviderSeed;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("smoke")
public class SmokeDefaultDataInitializer implements ApplicationRunner {

    private final ProviderConfigRepository providerConfigRepository;
    private final ApiKeyProtector apiKeyProtector;

    public SmokeDefaultDataInitializer(ProviderConfigRepository providerConfigRepository,
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
        return "sk-seed-" + providerName.toLowerCase().replaceAll("[^a-z0-9]+", "-") + UUID.randomUUID().toString().replace("-", "");
    }
}