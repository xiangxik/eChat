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
public class AnthropicCompatibleClient implements LlmProviderClient {

    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AnthropicCompatibleClient(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ProviderType providerType) {
        return providerType == ProviderType.ANTHROPIC;
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
            String responseBody = restClient.post()
                    .uri(endpoint(providerConfig.getBaseUrl(), "/messages"))
                    .headers(headers -> applyAuth(headers, apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(messagesRequest(modelConfig, List.of(new LlmChatMessage("user", "Reply with pong.", Map.of())), false))
                    .retrieve()
                .body(String.class);
            JsonNode response = json(responseBody);
            String content = content(response);
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
            String responseBody = restClient.post()
                    .uri(endpoint(providerConfig.getBaseUrl(), "/messages"))
                    .headers(headers -> applyAuth(headers, apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(messagesRequest(modelConfig, request.messages(), false))
                    .retrieve()
                .body(String.class);
            JsonNode response = json(responseBody);
            String content = content(response);
            String finishReason = response == null ? null : response.path("stop_reason").asText(null);
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
                    .uri(endpoint(providerConfig.getBaseUrl(), "/messages"))
                    .headers(headers -> applyAuth(headers, apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(messagesRequest(modelConfig, request.messages(), true))
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
                                JsonNode event = objectMapper.readTree(line.substring("data:".length()).strip());
                                String type = event.path("type").asText();
                                if ("content_block_delta".equals(type)) {
                                    String token = event.at("/delta/text").asText(null);
                                    if (StringUtils.hasText(token)) {
                                        message.append(token);
                                        eventConsumer.accept(LlmStreamEvent.token(token, message.toString()));
                                    }
                                } else if ("message_delta".equals(type)) {
                                    String finishReason = event.at("/delta/stop_reason").asText(null);
                                    if (StringUtils.hasText(finishReason)) {
                                        eventConsumer.accept(LlmStreamEvent.done(finishReason));
                                    }
                                } else if ("message_stop".equals(type)) {
                                    eventConsumer.accept(LlmStreamEvent.done("stop"));
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

    private Map<String, Object> messagesRequest(ModelConfig modelConfig, List<LlmChatMessage> messages, boolean stream) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", modelConfig.getModelName());
        String system = systemPrompt(messages);
        if (StringUtils.hasText(system)) {
            request.put("system", system);
        }
        request.put("messages", messages.stream()
                .filter(message -> !"system".equalsIgnoreCase(message.role()))
                .map(message -> Map.of("role", providerRole(message.role()), "content", message.content()))
                .toList());
        request.put("stream", stream);
        request.put("max_tokens", DEFAULT_MAX_TOKENS);
        if (modelConfig.getDefaultTemperature() != null) {
            request.put("temperature", modelConfig.getDefaultTemperature());
        }
        if (modelConfig.getDefaultTopP() != null) {
            request.put("top_p", modelConfig.getDefaultTopP());
        }
        return request;
    }

    private String systemPrompt(List<LlmChatMessage> messages) {
        return messages.stream()
                .filter(message -> "system".equalsIgnoreCase(message.role()))
                .map(LlmChatMessage::content)
                .filter(StringUtils::hasText)
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse(null);
    }

    private String providerRole(String role) {
        return "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
    }

    private String content(JsonNode response) {
        if (response == null) {
            return null;
        }
        StringBuilder content = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                content.append(block.path("text").asText());
            }
        }
        return content.toString();
    }

    private JsonNode json(String responseBody) throws com.fasterxml.jackson.core.JsonProcessingException {
        return StringUtils.hasText(responseBody) ? objectMapper.readTree(responseBody) : null;
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
            headers.set("X-Api-Key", apiKey);
        }
        headers.set("anthropic-version", ANTHROPIC_VERSION);
    }

    private String sanitize(String text) {
        if (!StringUtils.hasText(text)) {
            return "empty response body";
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}