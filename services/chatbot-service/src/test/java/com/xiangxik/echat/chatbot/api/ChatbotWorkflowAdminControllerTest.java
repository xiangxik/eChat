package com.xiangxik.echat.chatbot.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.xiangxik.echat.chatbot.service.ChatbotWorkflowService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatbotWorkflowAdminControllerTest extends PostgresIntegrationTest {

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
    private ChatbotConfigRepository chatbotConfigRepository;

    @Autowired
    private ApiKeyProtector apiKeyProtector;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void validatesSavesAndReadsChatbotWorkflow() throws Exception {
        Long chatbotId = createChatbot();
        Long modelId = createModel();
        Map<String, Object> request = workflowRequest(modelId);

        mockMvc.perform(post("/api/admin/chatbots/{chatbotId}/workflow/validate", chatbotId)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        mockMvc.perform(put("/api/admin/chatbots/{chatbotId}/workflow", chatbotId)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatbotId").value(chatbotId))
                .andExpect(jsonPath("$.nodes[?(@.nodeKey == 'Start' && @.name == 'Welcome Entry')]").exists())
                .andExpect(jsonPath("$.nodes[?(@.nodeKey == 'billing' && @.modelId == %d)]".formatted(modelId)).exists())
                .andExpect(jsonPath("$.transitions[0].conditionExpression").value("user.message contains 'billing'"));

        mockMvc.perform(get("/api/admin/chatbots/{chatbotId}/workflow", chatbotId)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.transitions.length()").value(1));
    }

    private Long createChatbot() {
        ChatbotConfig chatbot = new ChatbotConfig();
        chatbot.setName("Workflow bot " + UUID.randomUUID());
        chatbot.setEnabled(true);
        return chatbotConfigRepository.saveAndFlush(chatbot).getId();
    }

    private Long createModel() {
        String suffix = UUID.randomUUID().toString();
        ProviderConfig provider = new ProviderConfig();
        provider.setName("Workflow OpenAI " + suffix);
        provider.setType(ProviderType.OPENAI_COMPATIBLE);
        provider.setBaseUrl("http://localhost:8081/v1");
        provider.setEncryptedApiKey(apiKeyProtector.encrypt("sk-workflow-test"));
        provider.setEnabled(true);
        provider = providerConfigRepository.saveAndFlush(provider);

        ModelConfig model = new ModelConfig();
        model.setProvider(provider);
        model.setDisplayName("Workflow GPT " + suffix);
        model.setModelName("gpt-workflow");
        model.setModelType(ModelType.CHAT);
        model.setMaxContextTokens(8192);
        model.setSupportsStreaming(true);
        model.setEnabled(true);
        return modelConfigRepository.saveAndFlush(model).getId();
    }

    private Map<String, Object> workflowRequest(Long modelId) {
        return Map.of(
                "nodes", List.of(
                Map.of("nodeKey", "Start", "name", "Welcome Entry", "dslContent", ChatbotWorkflowService.DEFAULT_START_NODE_DSL,
                    "version", 1, "enabled", true, "start", true),
                Map.of("nodeKey", "billing", "name", "Billing", "dslContent", sampleDsl(),
                    "version", 1, "modelId", modelId, "enabled", true, "start", false)
                ),
                "transitions", List.of(
                Map.of("name", "Route to billing", "fromNodeKey", "Start", "toNodeKey", "billing",
                                "priority", 0, "enabled", true, "conditionExpression", "user.message contains 'billing'")
                )
        );
    }

    private String sampleDsl() {
        return """
                <contextPolicy name="workflow-test" maxTokens="12000">
                    <system priority="100">You are a helpful test assistant.</system>
                    <variables>
                        <var name="conversation" source="conversation.messages" maxMessages="20" />
                    </variables>
                    <output>
                        <section name="system" />
                        <section name="conversation" />
                    </output>
                </contextPolicy>
                """;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}