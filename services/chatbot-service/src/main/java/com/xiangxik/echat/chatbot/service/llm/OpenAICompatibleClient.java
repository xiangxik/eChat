package com.xiangxik.echat.chatbot.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAICompatibleClient implements LlmProviderClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAICompatibleClient(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ProviderType providerType) {
        return providerType == ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public LlmProviderTestResult testConnection(ProviderConfig providerConfig, String apiKey) {
        if (!StringUtils.hasText(providerConfig.getBaseUrl())) {
            return new LlmProviderTestResult(false, "Provider baseUrl is required", null, null);
        }
        try {
            Integer statusCode = restClient.get()
                    .uri(endpoint(providerConfig.getBaseUrl(), "/models"))
                    .headers(headers -> applyAuth(headers, apiKey))
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .value();
            return new LlmProviderTestResult(true, "Provider connection succeeded", statusCode, null);
        } catch (RestClientResponseException ex) {
            return new LlmProviderTestResult(false, "Provider connection failed: " + sanitize(ex.getResponseBodyAsString()),
                    ex.getStatusCode().value(), null);
        } catch (Exception ex) {
            return new LlmProviderTestResult(false, "Provider connection failed: " + ex.getMessage(), null, null);
        }
    }

    @Override
    public LlmProviderTestResult testGeneration(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey) {
        if (!StringUtils.hasText(providerConfig.getBaseUrl())) {
            return new LlmProviderTestResult(false, "Provider baseUrl is required", null, null);
        }
        try {
            JsonNode response = restClient.post()
                    .uri(endpoint(providerConfig.getBaseUrl(), "/chat/completions"))
                    .headers(headers -> applyAuth(headers, apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(chatCompletionRequest(modelConfig))
                    .retrieve()
                    .body(JsonNode.class);
            String content = response == null ? null : response.at("/choices/0/message/content").asText(null);
            return new LlmProviderTestResult(true, "Model generation succeeded", 200, content);
        } catch (RestClientResponseException ex) {
            return new LlmProviderTestResult(false, "Model generation failed: " + sanitize(ex.getResponseBodyAsString()),
                    ex.getStatusCode().value(), null);
        } catch (Exception ex) {
            return new LlmProviderTestResult(false, "Model generation failed: " + ex.getMessage(), null, null);
        }
    }

    @Override
    public LlmChatResponse chat(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey,
                                LlmChatRequest request) {
        ensureBaseUrl(providerConfig);
        try {
            JsonNode response = restClient.post()
                    .uri(endpoint(providerConfig.getBaseUrl(), "/chat/completions"))
                    .headers(headers -> applyAuth(headers, apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(chatCompletionRequest(modelConfig, request.messages(), false))
                    .retrieve()
                    .body(JsonNode.class);
            String content = response == null ? null : response.at("/choices/0/message/content").asText(null);
            String finishReason = response == null ? null : response.at("/choices/0/finish_reason").asText(null);
            if (!StringUtils.hasText(content)) {
                throw new LlmProviderException("Provider returned an empty assistant message");
            }
            return new LlmChatResponse(content, finishReason, Map.of());
        } catch (RestClientResponseException ex) {
            throw new LlmProviderException("Provider chat request failed: " + sanitize(ex.getResponseBodyAsString()), ex);
        } catch (LlmProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmProviderException("Provider chat request failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void streamChat(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey,
                           LlmChatRequest request, Consumer<LlmStreamEvent> eventConsumer) {
        ensureBaseUrl(providerConfig);
        StringBuilder message = new StringBuilder();
        try {
            restClient.post()
                    .uri(endpoint(providerConfig.getBaseUrl(), "/chat/completions"))
                    .headers(headers -> applyAuth(headers, apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(chatCompletionRequest(modelConfig, request.messages(), true))
                    .exchange((httpRequest, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new LlmProviderException("Provider stream request failed with HTTP "
                                    + response.getStatusCode().value());
                        }
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(),
                                StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String data = line.substring("data:".length()).strip();
                                if ("[DONE]".equals(data)) {
                                    eventConsumer.accept(LlmStreamEvent.done("stop"));
                                    break;
                                }
                                JsonNode chunk = objectMapper.readTree(data);
                                String token = chunk.at("/choices/0/delta/content").asText(null);
                                String finishReason = chunk.at("/choices/0/finish_reason").asText(null);
                                if (StringUtils.hasText(token)) {
                                    message.append(token);
                                    eventConsumer.accept(LlmStreamEvent.token(token, message.toString()));
                                }
                                if (StringUtils.hasText(finishReason)) {
                                    eventConsumer.accept(LlmStreamEvent.done(finishReason));
                                }
                            }
                        }
                        return null;
                    });
        } catch (LlmProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmProviderException("Provider stream request failed: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> chatCompletionRequest(ModelConfig modelConfig) {
        return chatCompletionRequest(modelConfig, List.of(new LlmChatMessage("user", "Reply with pong.", Map.of())), false);
    }

    private Map<String, Object> chatCompletionRequest(ModelConfig modelConfig, List<LlmChatMessage> messages,
                                                      boolean stream) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", modelConfig.getModelName());
        request.put("messages", messages.stream()
                .map(message -> Map.of("role", message.role().toLowerCase(), "content", message.content()))
                .toList());
        request.put("stream", stream);
        if (modelConfig.getDefaultTemperature() != null) {
            request.put("temperature", modelConfig.getDefaultTemperature());
        }
        if (modelConfig.getDefaultTopP() != null) {
            request.put("top_p", modelConfig.getDefaultTopP());
        }
        return request;
    }

    private void ensureBaseUrl(ProviderConfig providerConfig) {
        if (!StringUtils.hasText(providerConfig.getBaseUrl())) {
            throw new LlmProviderException("Provider baseUrl is required");
        }
    }

    private String endpoint(String baseUrl, String path) {
        return baseUrl.replaceAll("/+$", "") + path;
    }

    private void applyAuth(HttpHeaders headers, String apiKey) {
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
    }

    private String sanitize(String text) {
        if (!StringUtils.hasText(text)) {
            return "empty response body";
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}