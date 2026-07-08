package com.xiangxik.echat.chatbot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "echat")
public record ChatbotProperties(Service service, Llm llm, Context context, Security security, Bootstrap bootstrap) {

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

    public record Security(@NotBlank String apiKeyEncryptionSecret, @NotBlank String adminToken,
                           List<String> allowedOrigins, @Positive int chatRateLimitPerMinute,
                           @Positive int adminRateLimitPerMinute, boolean adminCookieSecure,
                           List<@Valid AdminPrincipalProperties> adminPrincipals) {
        public Security {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
            adminPrincipals = adminPrincipals == null ? List.of() : List.copyOf(adminPrincipals);
        }
    }

    public record AdminPrincipalProperties(
            @NotBlank String actorId,
            String displayName,
            @NotBlank String token,
            @NotBlank String tenantId,
            List<String> roles,
            Map<String, Object> attributes
    ) {
        public AdminPrincipalProperties {
            roles = roles == null ? List.of() : List.copyOf(roles);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    public record Bootstrap(List<@Valid ProviderSeedProperties> providers) {
        public Bootstrap {
            providers = providers == null ? List.of() : List.copyOf(providers);
        }
    }

    public record ProviderSeedProperties(
            @NotBlank String name,
            ProviderSeedType type,
            @NotBlank String baseUrl
    ) {
    }

    public enum ProviderSeedType {
        OPENAI_COMPATIBLE,
        ANTHROPIC,
        AZURE_OPENAI,
        GEMINI,
        OLLAMA,
        CUSTOM
    }
}