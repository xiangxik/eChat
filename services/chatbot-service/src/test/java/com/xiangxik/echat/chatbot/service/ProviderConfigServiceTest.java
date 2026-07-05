package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.PostgresIntegrationTest;
import com.xiangxik.echat.chatbot.api.dto.ProviderConfigRequest;
import com.xiangxik.echat.chatbot.api.dto.ProviderConfigResponse;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProviderConfigServiceTest extends PostgresIntegrationTest {

    @Autowired
    private ProviderConfigService providerConfigService;

    @Autowired
    private ProviderConfigRepository providerConfigRepository;

    @Test
    void createsProviderWithEncryptedApiKeyAndMaskedResponse() {
        ProviderConfigRequest request = new ProviderConfigRequest(
                "OpenAI Compatible",
                ProviderType.OPENAI_COMPATIBLE,
                "https://llm.example.com/v1",
                null,
                "sk-test-secret",
                true
        );

        ProviderConfigResponse response = providerConfigService.create(request);

        ProviderConfig providerConfig = providerConfigRepository.findById(response.id()).orElseThrow();
        assertThat(response.hasApiKey()).isTrue();
        assertThat(providerConfig.getEncryptedApiKey()).startsWith("v1:");
        assertThat(providerConfig.getEncryptedApiKey()).doesNotContain("sk-test-secret");
    }
}