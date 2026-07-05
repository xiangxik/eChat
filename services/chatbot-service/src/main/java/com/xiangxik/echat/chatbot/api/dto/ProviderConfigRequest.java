package com.xiangxik.echat.chatbot.api.dto;

import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Provider configuration create/update request")
public record ProviderConfigRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull ProviderType type,
        @Size(max = 1024) String baseUrl,
        @Size(max = 512) String apiKeySecretRef,
        @Size(max = 8192) String apiKey,
        Boolean enabled
) {
}