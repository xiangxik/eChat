package com.xiangxik.echat.chatbot.service.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenAICompatibleEmbeddingClient implements EmbeddingProviderClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAICompatibleEmbeddingClient(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ProviderType providerType) {
        return providerType == ProviderType.OPENAI_COMPATIBLE;
    }

    @Override
    public EmbeddingVector embed(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey, String input) {
        if (!StringUtils.hasText(providerConfig.getBaseUrl())) {
            throw new LlmProviderException("Provider baseUrl is required");
        }
        try {
            String body = restClient.post()
                    .uri(endpoint(providerConfig.getBaseUrl(), "/embeddings"))
                    .headers(headers -> applyAuth(headers, apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(embeddingRequest(modelConfig, input))
                    .retrieve()
                    .body(String.class);
            return parseEmbedding(body);
        } catch (RestClientResponseException ex) {
            throw new LlmProviderException("Embedding request failed: " + sanitize(ex.getResponseBodyAsString()), ex);
        } catch (LlmProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmProviderException("Embedding request failed: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> embeddingRequest(ModelConfig modelConfig, String input) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", modelConfig.getModelName());
        request.put("input", input);
        Object dimensions = modelConfig.getMetadata().getOrDefault("dimensions",
                modelConfig.getMetadata().get("embeddingDimension"));
        if (dimensions != null) {
            request.put("dimensions", dimensions);
        }
        return request;
    }

    private EmbeddingVector parseEmbedding(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode embedding = root.at("/data/0/embedding");
        if (!embedding.isArray() || embedding.isEmpty()) {
            throw new LlmProviderException("Embedding response did not contain data[0].embedding");
        }
        float[] values = new float[embedding.size()];
        for (int index = 0; index < embedding.size(); index++) {
            values[index] = (float) embedding.get(index).asDouble();
        }
        return new EmbeddingVector(values);
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