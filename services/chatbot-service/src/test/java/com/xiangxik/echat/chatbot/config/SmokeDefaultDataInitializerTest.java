package com.xiangxik.echat.chatbot.config;

import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.domain.repository.ModelConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import com.xiangxik.echat.chatbot.service.ApiKeyProtector;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("smoke")
class SmokeDefaultDataInitializerTest {

    @Autowired
    private ProviderConfigRepository providerConfigRepository;

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @Autowired
    private ApiKeyProtector apiKeyProtector;

    @Test
    void seedsDefaultProvidersAndRandomApiKeysWithoutModels() {
        Map<String, ProviderConfig> providersByName = providerConfigRepository.findAll().stream()
                .collect(Collectors.toMap(ProviderConfig::getName, provider -> provider));

        assertEquals(Set.of("Minimax Overseas", "Minimax China", "Qwen", "OpenAI", "Anthropic", "Gemini"), providersByName.keySet());
        assertEquals(ProviderType.ANTHROPIC, providersByName.get("Minimax Overseas").getType());
        assertEquals("https://api.minimax.io/anthropic/v1", providersByName.get("Minimax Overseas").getBaseUrl());
        assertEquals(ProviderType.OPENAI_COMPATIBLE, providersByName.get("Minimax China").getType());
        assertEquals("https://api.minimax.chat/v1", providersByName.get("Minimax China").getBaseUrl());
        for (ProviderConfig providerConfig : providersByName.values()) {
            assertFalse(providerConfig.isEnabled());
            assertNotNull(providerConfig.getBaseUrl());
            assertTrue(providerConfig.getEncryptedApiKey().startsWith("v1:"));
            assertTrue(apiKeyProtector.decrypt(providerConfig.getEncryptedApiKey()).startsWith("sk-seed-"));
        }

        assertEquals(0, modelConfigRepository.count());
    }
}