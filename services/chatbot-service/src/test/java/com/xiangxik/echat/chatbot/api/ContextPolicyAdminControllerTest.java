package com.xiangxik.echat.chatbot.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangxik.echat.chatbot.PostgresIntegrationTest;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ModelType;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.domain.repository.ModelConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ContextPolicyRepository;
import com.xiangxik.echat.chatbot.service.ApiKeyProtector;
import com.xiangxik.echat.chatbot.service.ContextPolicyService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
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
class ContextPolicyAdminControllerTest extends PostgresIntegrationTest {

    private static final String ADMIN_TOKEN = "test-admin-token";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

        @Autowired
        private ProviderConfigRepository providerConfigRepository;

        @Autowired
        private ModelConfigRepository modelConfigRepository;

        @Autowired
        private ContextPolicyRepository contextPolicyRepository;

        @Autowired
        private ApiKeyProtector apiKeyProtector;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void validatesSavesAndPreviewsContextPolicy() throws Exception {
                Long modelId = createModel();

        mockMvc.perform(post("/api/admin/context-policies/validate")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dslContent", sampleDsl()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.policyName").value("support-bot-v1"));

        String response = mockMvc.perform(post("/api/admin/context-policies")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Support Bot Context",
                                "description", "Context DSL v1 policy",
                                "dslContent", sampleDsl(),
                                "modelId", modelId,
                                "enabled", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Support Bot Context"))
                .andExpect(jsonPath("$.modelId").value(modelId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long policyId = JsonTestSupport.id(response);

        mockMvc.perform(post("/api/admin/context-policies/{id}/preview", policyId)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(previewRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].role").value("SYSTEM"))
                .andExpect(jsonPath("$.sections[?(@.name == 'conversation')]").exists())
                .andExpect(content().string(containsString("Need VPN help")));
    }

    @Test
    void returnsDslErrorsForInvalidPolicyOnSave() throws Exception {
                Long modelId = createModel();

        mockMvc.perform(post("/api/admin/context-policies")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Bad Policy",
                                "modelId", modelId,
                                "dslContent", """
                                        <contextPolicy name="bad" maxTokens="10">
                                          <variables>
                                            <var name="unknown" source="x" />
                                          </variables>
                                          <output><section name="unknown" /></output>
                                        </contextPolicy>
                                        """
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTEXT_DSL_INVALID"))
                .andExpect(content().string(containsString("Unsupported variable")));
    }

    @Test
    void exposesAndProtectsSystemManagedDefaultContextPolicy() throws Exception {
        Long defaultPolicyId = contextPolicyRepository.findByName(ContextPolicyService.DEFAULT_CONTEXT_POLICY_NAME)
                .orElseThrow()
                .getId();
        Long modelId = createModel();

        mockMvc.perform(get("/api/admin/context-policies/{id}", defaultPolicyId)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(ContextPolicyService.DEFAULT_CONTEXT_POLICY_NAME))
                .andExpect(jsonPath("$.systemManaged").value(true));

        mockMvc.perform(put("/api/admin/context-policies/{id}", defaultPolicyId)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", ContextPolicyService.DEFAULT_CONTEXT_POLICY_NAME,
                                "description", "Editable welcome policy",
                                "dslContent", sampleDsl(),
                                "modelId", modelId,
                                "enabled", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(ContextPolicyService.DEFAULT_CONTEXT_POLICY_NAME))
                .andExpect(jsonPath("$.description").value("Editable welcome policy"))
                .andExpect(jsonPath("$.systemManaged").value(true));

        mockMvc.perform(put("/api/admin/context-policies/{id}", defaultPolicyId)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Changed Default Policy",
                                "dslContent", sampleDsl(),
                                "modelId", modelId,
                                "enabled", true
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("System managed context policy name cannot be changed")));

        mockMvc.perform(delete("/api/admin/context-policies/{id}", defaultPolicyId)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("System managed context policies cannot be deleted")));
    }

        private Long createModel() {
                String suffix = UUID.randomUUID().toString();
                ProviderConfig provider = new ProviderConfig();
                provider.setName("Policy OpenAI " + suffix);
                provider.setType(ProviderType.OPENAI_COMPATIBLE);
                provider.setBaseUrl("http://localhost:8081/v1");
                provider.setEncryptedApiKey(apiKeyProtector.encrypt("sk-policy-test"));
                provider.setEnabled(true);
                provider = providerConfigRepository.saveAndFlush(provider);

                ModelConfig model = new ModelConfig();
                model.setProvider(provider);
                model.setDisplayName("Policy GPT " + suffix);
                model.setModelName("gpt-policy");
                model.setModelType(ModelType.CHAT);
                model.setSupportsStreaming(true);
                model.setEnabled(true);
                return modelConfigRepository.saveAndFlush(model).getId();
        }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private Map<String, Object> previewRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("chatbotId", 1);
        request.put("conversationId", 2);
        request.put("userId", "user-1");
        request.put("latestUserMessage", "Need VPN help");
        request.put("metadata", Map.of());
        request.put("conversation", List.of(Map.of("role", "USER", "content", "Hello", "metadata", Map.of())));
        request.put("shortTermMemory", List.of(Map.of("content", "Prefers concise answers", "score", 0.0,
                "metadata", Map.of())));
        request.put("longTermMemory", List.of(Map.of("content", "Had VPN issue", "score", 0.9,
                "metadata", Map.of())));
        request.put("userProfile", Map.of("department", "IT"));
        request.put("retrievalResults", List.of(Map.of("content", "VPN reset KB", "score", 0.88,
                "metadata", Map.of())));
        request.put("toolResults", List.of());
        request.put("runtime", Map.of("locale", "en-US"));
        return request;
    }

    private String sampleDsl() {
        return """
                <contextPolicy name="support-bot-v1" maxTokens="12000">
                  <system priority="100">
                    You are a helpful enterprise support assistant.
                  </system>
                  <variables>
                    <var name="conversation" source="conversation.messages" maxMessages="20" />
                    <var name="shortTermMemory" source="memory.shortTerm" limit="10" />
                    <var name="longTermMemory" source="memory.longTerm" topK="8" minScore="0.72" />
                    <var name="userProfile" source="user.profile" optional="true" />
                    <var name="retrievalResults" source="retrieval.vector" topK="6" />
                  </variables>
                  <budget>
                    <reserve target="system" tokens="1200" />
                    <reserve target="conversation" tokens="5000" />
                    <reserve target="longTermMemory" tokens="2500" />
                    <reserve target="retrievalResults" tokens="2500" />
                  </budget>
                  <rules>
                    <include when="conversation.latestUserMessage.exists" target="conversation" />
                    <include when="longTermMemory.score &gt; 0.72" target="longTermMemory" />
                    <exclude when="metadata.sensitive == true" target="retrievalResults" />
                    <truncate target="conversation" strategy="oldest-first" />
                  </rules>
                  <output>
                    <section name="system" />
                    <section name="userProfile" optional="true" />
                    <section name="shortTermMemory" optional="true" />
                    <section name="longTermMemory" optional="true" />
                    <section name="conversation" />
                    <section name="retrievalResults" optional="true" />
                  </output>
                </contextPolicy>
                """;
    }
}