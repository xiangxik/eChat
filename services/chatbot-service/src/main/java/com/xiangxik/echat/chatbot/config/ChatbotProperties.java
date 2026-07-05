package com.xiangxik.echat.chatbot.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "echat")
public record ChatbotProperties(Service service, Llm llm, Context context, Security security) {

    public record Service(@NotBlank String version) {
    }

    public record Llm(
            @NotBlank String provider,
            @NotBlank String model,
            String baseUrl,
            String apiKey,
            @Positive double temperature,
            @Positive int maxTokens
    ) {
    }

    public record Context(@Positive int defaultMaxTokens, @Positive int maxConversationMessages,
                          @Positive int embeddingDimension) {
    }

    public record Security(@NotBlank String apiKeyEncryptionSecret, @NotBlank String adminToken) {
    }
}