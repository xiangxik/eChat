package com.xiangxik.echat.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xiangxik.echat.chatbot.api.dto.ModelOptionResponse;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderModelDiscoveryServiceTest {

    private HttpServer server;
    private ProviderModelDiscoveryService service;
    private String lastApiKey;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/anthropic/v1/models", this::handleModels);
        server.start();
        service = new ProviderModelDiscoveryService(new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void listsAnthropicCompatibleModels() {
        List<ModelOptionResponse> models = service.listModels(provider(), "sk-test");

        assertEquals("sk-test", lastApiKey);
        assertEquals(List.of("MiniMax-M2.7", "MiniMax-M3"), models.stream()
                .map(ModelOptionResponse::modelName)
                .toList());
        assertEquals("MiniMax-M3", models.get(1).displayName());
    }

    private void handleModels(HttpExchange exchange) throws IOException {
        lastApiKey = exchange.getRequestHeaders().getFirst("X-Api-Key");
        writeJson(exchange, """
                {"data":[{"id":"MiniMax-M3","display_name":"MiniMax-M3","type":"model"},{"id":"MiniMax-M2.7","display_name":"MiniMax-M2.7","type":"model"}],"has_more":false}
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
}