package com.xiangxik.echat.chatbot.api;

import com.xiangxik.echat.chatbot.PostgresIntegrationTest;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.api.dto.ModelOptionResponse;
import com.xiangxik.echat.chatbot.domain.model.ModelType;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.service.ProviderModelDiscoveryService;
import com.xiangxik.echat.chatbot.service.llm.LlmChatRequest;
import com.xiangxik.echat.chatbot.service.llm.LlmChatResponse;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClient;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClientRegistry;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderTestResult;
import com.xiangxik.echat.chatbot.service.llm.LlmStreamEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProviderModelAdminControllerTest extends PostgresIntegrationTest {

    private static final String ADMIN_TOKEN = "test-admin-token";
    private static final String VIEWER_TOKEN = "test-viewer-token";
    private static final String API_KEY = "sk-admin-secret";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void rejectsAdminRequestsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/providers"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("UNAUTHORIZED")));
    }

    @Test
    void openApiDocumentsProviderAndModelAdminEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/admin/providers/{id}/test-connection")))
                .andExpect(content().string(containsString("/api/admin/models/options")))
                .andExpect(content().string(containsString("/api/admin/models/{id}/test-generation")))
                .andExpect(content().string(containsString("AdminToken")))
                .andExpect(content().string(containsString("X-Admin-Token")))
                .andExpect(content().string(containsString("AdminSessionCookie")))
                .andExpect(content().string(containsString("echat_admin_session")));
    }

    @Test
    void listsKnownModelOptionsForProvider() throws Exception {
        Long providerId = createProvider();

        mockMvc.perform(get("/api/admin/models/options")
                        .param("providerId", providerId.toString())
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.modelName == 'gpt-4.1')]").exists())
            .andExpect(jsonPath("$[?(@.modelName == 'provider-live-model')]").exists());
    }

        @Test
        void rbacAllowsViewerReadsButRejectsWrites() throws Exception {
        mockMvc.perform(get("/api/admin/providers")
                .header("X-Admin-Token", VIEWER_TOKEN))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/providers")
                .header("X-Admin-Token", VIEWER_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"Viewer Write Attempt",
                      "type":"OPENAI_COMPATIBLE",
                      "baseUrl":"http://localhost:8081/v1",
                      "apiKey":"%s",
                      "enabled":true
                    }
                    """.formatted(API_KEY)))
            .andExpect(status().isForbidden())
            .andExpect(content().string(containsString("FORBIDDEN")));
        }

        @Test
        void abacRejectsCrossTenantAdminAccess() throws Exception {
        mockMvc.perform(get("/api/admin/providers")
                .header("X-Admin-Token", VIEWER_TOKEN)
                .header("X-Tenant-Id", "tenant-b"))
            .andExpect(status().isForbidden())
            .andExpect(content().string(containsString("FORBIDDEN")));
        }

    @Test
    void createsUpdatesListsAndDeletesProviderAndModelWithoutLeakingApiKey() throws Exception {
        Long providerId = createProvider();

        mockMvc.perform(get("/api/admin/providers/{id}", providerId)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApiKey").value(true))
                .andExpect(content().string(not(containsString(API_KEY))));

        mockMvc.perform(get("/api/admin/audit-logs")
                .header("X-Admin-Token", ADMIN_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.eventType == 'PROVIDER_CREATED')]").exists())
            .andExpect(jsonPath("$[?(@.eventType == 'PROVIDER_CREATED' && @.actorId == 'test-admin')]").exists())
            .andExpect(jsonPath("$[?(@.eventType == 'PROVIDER_CREATED' && @.tenantId == 'tenant-a')]").exists())
            .andExpect(jsonPath("$[?(@.eventType == 'PROVIDER_CREATED' && @.metadata.actorDisplayName == 'Test Admin')]").exists())
            .andExpect(content().string(not(containsString(API_KEY))));

        mockMvc.perform(put("/api/admin/providers/{id}", providerId)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"OpenAI Compatible Updated",
                                  "type":"OPENAI_COMPATIBLE",
                                  "baseUrl":"http://localhost:8081/v1",
                                  "enabled":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("OpenAI Compatible Updated"))
                .andExpect(jsonPath("$.hasApiKey").value(true))
                .andExpect(content().string(not(containsString(API_KEY))));

        Long modelId = createModel(providerId);

        mockMvc.perform(get("/api/admin/models")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].providerId").value(providerId))
                .andExpect(jsonPath("$[0].supportsStreaming").value(true));

        mockMvc.perform(put("/api/admin/models/{id}", modelId)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId":%d,
                                  "displayName":"GPT Test Updated",
                                  "modelName":"gpt-test",
                                  "modelType":"CHAT",
                                  "maxContextTokens":16000,
                                  "defaultTemperature":0.1,
                                  "defaultTopP":0.9,
                                  "supportsStreaming":false,
                                  "enabled":true,
                                  "metadata":{"tier":"test"}
                                }
                                """.formatted(providerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("GPT Test Updated"))
                .andExpect(jsonPath("$.supportsStreaming").value(false));

        mockMvc.perform(delete("/api/admin/models/{id}", modelId)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/admin/providers/{id}", providerId)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void testEndpointsReturnClearProviderAndModelResults() throws Exception {
        Long providerId = createProvider();
        Long modelId = createModel(providerId);

        mockMvc.perform(post("/api/admin/providers/{id}/test-connection", providerId)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.providerType").value("OPENAI_COMPATIBLE"))
                .andExpect(jsonPath("$.statusCode").value(200));

        mockMvc.perform(post("/api/admin/models/{id}/test-generation", modelId)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.model").value("gpt-test"))
                .andExpect(jsonPath("$.sampleText").value("pong"));
    }

    private Long createProvider() throws Exception {
        String response = mockMvc.perform(post("/api/admin/providers")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"OpenAI Compatible",
                                  "type":"OPENAI_COMPATIBLE",
                                  "baseUrl":"http://localhost:8081/v1",
                                  "apiKey":"%s",
                                  "enabled":true
                                }
                                """.formatted(API_KEY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasApiKey").value(true))
                .andExpect(content().string(not(containsString(API_KEY))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonTestSupport.id(response);
    }

    private Long createModel(Long providerId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/models")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerId":%d,
                                  "displayName":"GPT Test",
                                  "modelName":"gpt-test",
                                  "modelType":"CHAT",
                                  "maxContextTokens":8192,
                                  "defaultTemperature":0.2,
                                  "defaultTopP":0.9,
                                  "supportsStreaming":true,
                                  "enabled":true,
                                  "metadata":{"tier":"test"}
                                }
                                """.formatted(providerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modelName").value("gpt-test"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonTestSupport.id(response);
    }

    @TestConfiguration
    static class TestLlmClientConfiguration {

        @Bean
        @Primary
        ProviderModelDiscoveryService testProviderModelDiscoveryService() {
            return new ProviderModelDiscoveryService() {
                @Override
                public List<ModelOptionResponse> listModels(ProviderConfig providerConfig, String apiKey) {
                    if (!API_KEY.equals(apiKey)) {
                        throw new IllegalArgumentException("API key was not decrypted");
                    }
                    return List.of(
                            new ModelOptionResponse("GPT 4.1", "gpt-4.1", ModelType.CHAT, null, 0.2, 0.9, true),
                            new ModelOptionResponse("Provider Live Model", "provider-live-model", ModelType.CHAT, null, 0.2, 0.9, true)
                    );
                }
            };
        }

        @Bean
        @Primary
        LlmProviderClientRegistry testClientRegistry() {
            return new LlmProviderClientRegistry(List.of(new LlmProviderClient() {
                @Override
                public boolean supports(ProviderType providerType) {
                    return providerType == ProviderType.OPENAI_COMPATIBLE;
                }

                @Override
                public LlmProviderTestResult testConnection(ProviderConfig providerConfig, String apiKey) {
                    if (!API_KEY.equals(apiKey)) {
                        return new LlmProviderTestResult(false, "API key was not decrypted", 401, null);
                    }
                    return new LlmProviderTestResult(true, "Provider connection succeeded", 200, null);
                }

                @Override
                public LlmProviderTestResult testGeneration(ProviderConfig providerConfig, ModelConfig modelConfig,
                                                            String apiKey) {
                    if (!API_KEY.equals(apiKey)) {
                        return new LlmProviderTestResult(false, "API key was not decrypted", 401, null);
                    }
                    return new LlmProviderTestResult(true, "Model generation succeeded", 200, "pong");
                }

                @Override
                public LlmChatResponse chat(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey,
                                            LlmChatRequest request) {
                    return new LlmChatResponse("pong", "stop", Map.of());
                }

                @Override
                public void streamChat(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey,
                                       LlmChatRequest request, Consumer<LlmStreamEvent> eventConsumer) {
                    eventConsumer.accept(LlmStreamEvent.token("pong", "pong"));
                    eventConsumer.accept(LlmStreamEvent.done("stop"));
                }
            }));
        }
    }
}