package com.xiangxik.echat.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangxik.echat.chatbot.api.dto.ModelOptionResponse;
import com.xiangxik.echat.chatbot.domain.model.ModelType;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderException;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ProviderModelDiscoveryService {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProviderModelDiscoveryService() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    public ProviderModelDiscoveryService(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    public List<ModelOptionResponse> listModels(ProviderConfig providerConfig, String apiKey) {
        if (!StringUtils.hasText(providerConfig.getBaseUrl())) {
            throw new IllegalArgumentException("Provider baseUrl is required before loading models");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("Provider API key is required before loading models");
        }

        try {
            return switch (providerConfig.getType()) {
                case OPENAI_COMPATIBLE, AZURE_OPENAI, OLLAMA, CUSTOM -> listOpenAICompatibleModels(providerConfig, apiKey);
                case ANTHROPIC -> listAnthropicModels(providerConfig, apiKey);
                case GEMINI -> listGeminiModels(providerConfig, apiKey);
            };
        } catch (RestClientResponseException ex) {
            throw new LlmProviderException("Unable to load provider models: HTTP " + ex.getStatusCode().value() + " "
                    + sanitize(ex.getResponseBodyAsString()), ex);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmProviderException("Unable to load provider models: " + ex.getMessage(), ex);
        }
    }

    private List<ModelOptionResponse> listOpenAICompatibleModels(ProviderConfig providerConfig, String apiKey) {
        String responseBody = restClient.get()
                .uri(endpoint(providerConfig.getBaseUrl(), "/models"))
                .headers(headers -> headers.setBearerAuth(apiKey))
                .retrieve()
            .body(String.class);
        JsonNode response = json(responseBody);
        return streamArray(response, "data")
                .map(model -> modelOption(model.path("id").asText()))
                .sorted(Comparator.comparing(ModelOptionResponse::modelName))
                .toList();
    }

    private List<ModelOptionResponse> listAnthropicModels(ProviderConfig providerConfig, String apiKey) {
        String responseBody = restClient.get()
                .uri(endpoint(providerConfig.getBaseUrl(), "/models"))
                .headers(headers -> {
                    headers.set("X-Api-Key", apiKey);
                    headers.set("anthropic-version", ANTHROPIC_VERSION);
                })
                .retrieve()
                .body(String.class);
        JsonNode response = json(responseBody);
        return streamArray(response, "data")
                .map(model -> modelOption(model.path("display_name").asText(model.path("id").asText()), model.path("id").asText()))
                .sorted(Comparator.comparing(ModelOptionResponse::modelName))
                .toList();
    }

    private List<ModelOptionResponse> listGeminiModels(ProviderConfig providerConfig, String apiKey) {
        String responseBody = restClient.get()
                .uri(endpoint(providerConfig.getBaseUrl(), "/models"))
                .headers(headers -> headers.set("x-goog-api-key", apiKey))
                .retrieve()
            .body(String.class);
        JsonNode response = json(responseBody);
        return streamArray(response, "models")
                .map(model -> {
                    String fullName = model.path("name").asText();
                    String modelName = fullName.replaceFirst("^models/", "");
                    return modelOption(model.path("displayName").asText(modelName), modelName);
                })
                .sorted(Comparator.comparing(ModelOptionResponse::modelName))
                .toList();
    }

    private java.util.stream.Stream<JsonNode> streamArray(JsonNode response, String fieldName) {
        JsonNode arrayNode = response == null ? null : response.path(fieldName);
        if (arrayNode == null || !arrayNode.isArray()) {
            return java.util.stream.Stream.empty();
        }
        return java.util.stream.StreamSupport.stream(arrayNode.spliterator(), false);
    }

    private ModelOptionResponse modelOption(String modelName) {
        return modelOption(toDisplayName(modelName), modelName);
    }

    private ModelOptionResponse modelOption(String displayName, String modelName) {
        ModelType modelType = inferModelType(modelName);
        return new ModelOptionResponse(
                displayName,
                modelName,
                modelType,
                null,
                modelType.isChat() ? 0.2 : null,
                modelType.isChat() ? 0.9 : null,
                modelType.isChat()
        );
    }

    private ModelType inferModelType(String modelName) {
        String normalizedModelName = modelName.toLowerCase();
        if (normalizedModelName.contains("embedding") || normalizedModelName.contains("embed")) {
            return ModelType.EMBEDDING;
        }
        if (normalizedModelName.contains("rerank")) {
            return ModelType.RERANKER;
        }
        return ModelType.CHAT;
    }

    private String toDisplayName(String modelName) {
        return modelName.replace('-', ' ');
    }

    private String endpoint(String baseUrl, String path) {
        return baseUrl.replaceAll("/+$", "") + path;
    }

    private JsonNode json(String responseBody) {
        try {
            return StringUtils.hasText(responseBody) ? objectMapper.readTree(responseBody) : null;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Provider returned invalid JSON", ex);
        }
    }

    private String sanitize(String text) {
        if (!StringUtils.hasText(text)) {
            return "empty response body";
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}