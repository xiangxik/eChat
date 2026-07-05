package com.xiangxik.echat.chatbot.api.dto;

import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Provider configuration response without plaintext API keys")
public record ProviderConfigResponse(
        Long id,
        String name,
        ProviderType type,
        String baseUrl,
        String apiKeySecretRef,
        boolean hasApiKey,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}