package com.xiangxik.echat.chatbot.api;

import com.xiangxik.echat.chatbot.PostgresIntegrationTest;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ModelType;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ModelConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import com.xiangxik.echat.chatbot.service.ApiKeyProtector;
import com.xiangxik.echat.chatbot.service.embedding.EmbeddingProviderClient;
import com.xiangxik.echat.chatbot.service.embedding.EmbeddingProviderClientRegistry;
import com.xiangxik.echat.chatbot.service.embedding.EmbeddingVector;
import java.util.List;
import java.util.Map;
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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemoryAdminControllerTest extends PostgresIntegrationTest {

    private static final String ADMIN_TOKEN = "test-admin-token";
    private static final String API_KEY = "sk-memory-secret";
    private static final String TENANT_ID = "tenant-a";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ProviderConfigRepository providerConfigRepository;

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @Autowired
    private ChatbotConfigRepository chatbotConfigRepository;

    @Autowired
    private ApiKeyProtector apiKeyProtector;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void createsListsSearchesAndDeletesMemoryWithoutLeakingApiKey() throws Exception {
        Long chatbotId = createFixtures();

        String vpnResponse = mockMvc.perform(post("/api/admin/memories")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatbotId":%d,
                                  "userId":"user-1",
                                  "scope":"LONG_TERM",
                                  "content":"User has a recurring VPN outage on Mondays.",
                                  "metadata":{"source":"admin-test"}
                                }
                                """.formatted(chatbotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.embeddingDimension").value(1536))
                .andExpect(content().string(not(containsString(API_KEY))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long memoryId = JsonTestSupport.id(vpnResponse);

        mockMvc.perform(post("/api/admin/memories")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatbotId":%d,
                                  "userId":"user-1",
                                  "scope":"LONG_TERM",
                                  "content":"User asked about billing invoice exports.",
                                  "metadata":{"source":"admin-test"}
                                }
                                """.formatted(chatbotId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/memories")
                        .param("chatbotId", chatbotId.toString())
                        .param("scope", "LONG_TERM")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.content =~ /.*VPN.*/)]").exists());

        mockMvc.perform(post("/api/admin/memories/search")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatbotId":%d,
                                  "userId":"user-1",
                                  "query":"VPN is down again",
                                  "topK":3,
                                  "minScore":0.70
                                }
                                """.formatted(chatbotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(memoryId))
                .andExpect(jsonPath("$[0].score", greaterThan(0.99)))
                .andExpect(jsonPath("$[0].content").value(containsString("VPN")))
                .andExpect(content().string(not(containsString("billing invoice"))));

        mockMvc.perform(delete("/api/admin/memories/{id}", memoryId)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());
    }

    private Long createFixtures() {
        ProviderConfig provider = new ProviderConfig();
        provider.setTenantId(TENANT_ID);
        provider.setName("Memory Embedding Provider");
        provider.setType(ProviderType.OPENAI_COMPATIBLE);
        provider.setBaseUrl("http://localhost:8081/v1");
        provider.setEncryptedApiKey(apiKeyProtector.encrypt(API_KEY));
        provider.setEnabled(true);
        provider = providerConfigRepository.save(provider);

        ModelConfig model = new ModelConfig();
        model.setProvider(provider);
        model.setDisplayName("Memory Embedding Model");
        model.setModelName("text-embedding-test");
        model.setModelType(ModelType.EMBEDDING);
        model.setSupportsStreaming(false);
        model.setEnabled(true);
        model.setMetadata(Map.of("embeddingDimension", 1536));
        modelConfigRepository.save(model);

        ChatbotConfig chatbot = new ChatbotConfig();
        chatbot.setTenantId(TENANT_ID);
        chatbot.setName("Memory Test Chatbot");
        chatbot.setDescription("Memory admin test chatbot");
        chatbot.setEnabled(true);
        return chatbotConfigRepository.save(chatbot).getId();
    }

    @TestConfiguration
    static class TestEmbeddingConfiguration {

        @Bean
        @Primary
        EmbeddingProviderClientRegistry testEmbeddingProviderClientRegistry() {
            return new EmbeddingProviderClientRegistry(List.of(new EmbeddingProviderClient() {
                @Override
                public boolean supports(ProviderType providerType) {
                    return providerType == ProviderType.OPENAI_COMPATIBLE;
                }

                @Override
                public EmbeddingVector embed(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey,
                                             String input) {
                    if (!API_KEY.equals(apiKey)) {
                        throw new IllegalArgumentException("API key was not decrypted");
                    }
                    return new EmbeddingVector(vectorFor(input));
                }
            }));
        }

        private static float[] vectorFor(String input) {
            float[] vector = new float[1536];
            String normalized = input == null ? "" : input.toLowerCase();
            if (normalized.contains("vpn")) {
                vector[0] = 1.0f;
            } else {
                vector[1] = 1.0f;
            }
            return vector;
        }
    }
}