package com.xiangxik.echat.chatbot.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicCompatibleClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private AnthropicCompatibleClient client;
    private String lastRequestBody;
    private String lastApiKey;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/anthropic/v1/models", this::handleModels);
        server.createContext("/anthropic/v1/messages", this::handleMessages);
        server.start();
        client = new AnthropicCompatibleClient(objectMapper);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void callsAnthropicCompatibleMessagesEndpoint() throws Exception {
        LlmChatRequest request = new LlmChatRequest(
                java.util.List.of(
                        new LlmChatMessage("system", "Follow the policy.", Map.of()),
                        new LlmChatMessage("user", "Hello", Map.of())
                ),
                Map.of(), "request-id", "trace-id", null);

        LlmChatResponse response = client.chat(provider(), model(), "sk-test", request);

        JsonNode body = objectMapper.readTree(lastRequestBody);
        assertEquals("pong", response.content());
        assertEquals("end_turn", response.finishReason());
        assertEquals("sk-test", lastApiKey);
        assertEquals("MiniMax-M3", body.path("model").asText());
        assertEquals("Follow the policy.", body.path("system").asText());
        assertEquals("user", body.at("/messages/0/role").asText());
        assertEquals("Hello", body.at("/messages/0/content").asText());
        assertEquals(1024, body.path("max_tokens").asInt());
    }

    @Test
    void testsConnectionAgainstModelsEndpoint() {
        LlmProviderTestResult result = client.testConnection(provider(), "sk-test");

        assertTrue(result.success());
        assertEquals(200, result.statusCode());
        assertEquals("sk-test", lastApiKey);
    }

    private void handleModels(HttpExchange exchange) throws IOException {
        lastApiKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
        writeJson(exchange, """
                {"data":[{"id":"MiniMax-M3","display_name":"MiniMax-M3","type":"model"}],"has_more":false}
                """);
    }

    private void handleMessages(HttpExchange exchange) throws IOException {
        lastApiKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
        lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        writeJson(exchange, """
                {"id":"msg_1","type":"message","role":"assistant","model":"MiniMax-M3","content":[{"type":"text","text":"pong"}],"stop_reason":"end_turn"}
                """);
    }

    private void writeJson(HttpExchange exchange, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private ProviderConfig provider() {
        ProviderConfig providerConfig = new ProviderConfig();
        providerConfig.setType(ProviderType.ANTHROPIC);
        providerConfig.setBaseUrl("http://localhost:" + server.getAddress().getPort() + "/anthropic/v1");
        return providerConfig;
    }

    private ModelConfig model() {
        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setModelName("MiniMax-M3");
        modelConfig.setDefaultTemperature(0.2);
        modelConfig.setDefaultTopP(0.95);
        return modelConfig;
    }
}