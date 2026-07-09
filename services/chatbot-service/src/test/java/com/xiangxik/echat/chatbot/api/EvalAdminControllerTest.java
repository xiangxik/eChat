package com.xiangxik.echat.chatbot.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangxik.echat.chatbot.PostgresIntegrationTest;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ChatbotWorkflowNode;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ModelType;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotWorkflowNodeRepository;
import com.xiangxik.echat.chatbot.domain.repository.ConversationRepository;
import com.xiangxik.echat.chatbot.domain.repository.ModelConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import com.xiangxik.echat.chatbot.service.ApiKeyProtector;
import com.xiangxik.echat.chatbot.service.llm.LlmChatRequest;
import com.xiangxik.echat.chatbot.service.llm.LlmChatResponse;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClient;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClientRegistry;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderTestResult;
import com.xiangxik.echat.chatbot.service.llm.LlmStreamEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class EvalAdminControllerTest extends PostgresIntegrationTest {

    private static final String ADMIN_TOKEN = "test-admin-token";
    private static final String API_KEY = "sk-eval-secret";
    private static final String TENANT_ID = "tenant-a";

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
    private ChatbotWorkflowNodeRepository workflowNodeRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ApiKeyProtector apiKeyProtector;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void createsDatasetCaseRunsEvalAndStoresIsolatedResults() throws Exception {
        Long chatbotId = createConfiguredChatbot();
        long conversationsBefore = conversationRepository.count();

        String datasetJson = mockMvc.perform(post("/api/admin/eval-datasets")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Support eval " + UUID.randomUUID(),
                                "description", "Smoke eval dataset",
                                "chatbotId", chatbotId
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chatbotId").value(chatbotId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long datasetId = JsonTestSupport.id(datasetJson);

        String caseJson = mockMvc.perform(post("/api/admin/eval-datasets/{id}/cases", datasetId)
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "input", "How do I reset VPN?",
                                "expectedBehavior", "Mentions VPN reset steps",
                                "expectedKeywords", List.of("VPN", "reset"),
                            "metadata", Map.of(
                                "apiKey", "should-not-leak",
                                "topic", "vpn",
                                "goldenConversation", List.of(
                                    Map.of("role", "USER", "content", "My VPN client is failing."),
                                    Map.of("role", "ASSISTANT", "content", "Check your VPN profile first.")
                                )
                            )
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datasetId").value(datasetId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long caseId = JsonTestSupport.id(caseJson);

        String runJson = mockMvc.perform(post("/api/admin/eval-runs")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "datasetId", datasetId,
                                "maxEstimatedTokens", 2000,
                            "maxLatencyMillis", 30000,
                            "maxEstimatedCostUsd", 1.0,
                            "costPer1kTokensUsd", 0.002,
                            "forbiddenPhrases", List.of("do not know"),
                            "rubric", Map.of(
                                "minScore", 1.0,
                                "criteria", List.of(Map.of(
                                    "name", "vpn reset coverage",
                                    "required", true,
                                    "keywords", List.of("VPN", "reset")
                                ))
                            ),
                            "releaseGate", Map.of(
                                "minPassRate", 1.0,
                                "maxFailedCases", 0,
                                "maxAverageLatencyMillis", 30000,
                                "maxEstimatedCostUsd", 1.0
                            )
                        ))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.datasetId").value(datasetId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long runId = JsonTestSupport.id(runJson);

        JsonNode completedRun = awaitCompletedRun(runId);
        assertThat(completedRun.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completedRun.path("summary").path("passedCases").asInt()).isEqualTo(1);
        assertThat(completedRun.path("summary").path("releaseGatePassed").asBoolean()).isTrue();
        assertThat(completedRun.path("summary").path("metrics").path("totalEstimatedCostUsd").asDouble()).isGreaterThanOrEqualTo(0.0);

        mockMvc.perform(get("/api/admin/eval-runs/{id}/results", runId)
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].caseId").value(caseId))
                .andExpect(jsonPath("$[0].output").value("assistant: How do I reset VPN?"))
                .andExpect(jsonPath("$[0].passed").value(true))
                .andExpect(jsonPath("$[0].contextSnapshot.isolated").value(true))
                .andExpect(jsonPath("$[0].contextSnapshot.goldenReplay.replayedMessages").value(2))
                .andExpect(jsonPath("$[0].contextSnapshot.messages").exists())
                .andExpect(jsonPath("$[0].tokenBudgetReport.totalEstimatedTokens").exists())
                .andExpect(jsonPath("$[0].scores.keywordMatch.passed").value(true))
                .andExpect(jsonPath("$[0].scores.rubricScoring.passed").value(true))
                .andExpect(jsonPath("$[0].scores.latencyBudget.passed").value(true))
                .andExpect(jsonPath("$[0].scores.costBudget.passed").value(true))
                .andExpect(jsonPath("$[0].scores.metrics.latencyMillis").exists())
                .andExpect(jsonPath("$[0].scores.metrics.estimatedCostUsd").exists());

        assertThat(conversationRepository.count()).isEqualTo(conversationsBefore);
    }

    private JsonNode awaitCompletedRun(Long runId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        JsonNode run = null;
        while (System.currentTimeMillis() < deadline) {
            String runJson = mockMvc.perform(get("/api/admin/eval-runs/{id}", runId)
                            .header("X-Admin-Token", ADMIN_TOKEN))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            run = objectMapper.readTree(runJson);
            if (List.of("COMPLETED", "FAILED").contains(run.path("status").asText())) {
                return run;
            }
            Thread.sleep(100);
        }
        return run;
    }

    private Long createConfiguredChatbot() {
        String suffix = UUID.randomUUID().toString();
        ProviderConfig provider = new ProviderConfig();
        provider.setTenantId(TENANT_ID);
        provider.setName("Eval OpenAI " + suffix);
        provider.setType(ProviderType.OPENAI_COMPATIBLE);
        provider.setBaseUrl("http://localhost:8081/v1");
        provider.setEncryptedApiKey(apiKeyProtector.encrypt(API_KEY));
        provider.setEnabled(true);
        provider = providerConfigRepository.saveAndFlush(provider);

        ModelConfig model = new ModelConfig();
        model.setProvider(provider);
        model.setDisplayName("Eval GPT " + suffix);
        model.setModelName("gpt-eval");
        model.setModelType(ModelType.CHAT);
        model.setMaxContextTokens(8192);
        model.setSupportsStreaming(true);
        model.setEnabled(true);
        model = modelConfigRepository.saveAndFlush(model);

        ChatbotConfig chatbot = new ChatbotConfig();
        chatbot.setTenantId(TENANT_ID);
        chatbot.setName("Eval bot " + suffix);
        chatbot.setEnabled(true);
        chatbot = chatbotConfigRepository.saveAndFlush(chatbot);

        ChatbotWorkflowNode node = new ChatbotWorkflowNode();
        node.setChatbot(chatbot);
        node.setNodeKey("start");
        node.setName("Start");
        node.setDslContent(evalPolicyDsl());
        node.setVersion(1);
        node.setModel(model);
        node.setStart(true);
        node.setEnabled(true);
        workflowNodeRepository.saveAndFlush(node);
        return chatbot.getId();
    }

    private String evalPolicyDsl() {
        return """
                <contextPolicy name="eval-test" maxTokens="12000">
                    <system priority="100">You are a helpful test assistant.</system>
                    <variables>
                        <var name="conversation" source="conversation.messages" maxMessages="20" />
                        <var name="metadata" source="metadata" optional="true" />
                        <var name="runtime" source="runtime" optional="true" />
                    </variables>
                    <output>
                        <section name="system" />
                        <section name="conversation" />
                        <section name="metadata" optional="true" />
                        <section name="runtime" optional="true" />
                    </output>
                </contextPolicy>
                """;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @TestConfiguration
    static class EvalLlmClientConfiguration {

        @Bean
        @Primary
        LlmProviderClientRegistry evalClientRegistry() {
            return new LlmProviderClientRegistry(List.of(new LlmProviderClient() {
                @Override
                public boolean supports(ProviderType providerType) {
                    return providerType == ProviderType.OPENAI_COMPATIBLE;
                }

                @Override
                public LlmProviderTestResult testConnection(ProviderConfig providerConfig, String apiKey) {
                    return new LlmProviderTestResult(API_KEY.equals(apiKey), "ok", 200, null);
                }

                @Override
                public LlmProviderTestResult testGeneration(ProviderConfig providerConfig, ModelConfig modelConfig,
                                                            String apiKey) {
                    return new LlmProviderTestResult(API_KEY.equals(apiKey), "ok", 200, "pong");
                }

                @Override
                public LlmChatResponse chat(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey,
                                            LlmChatRequest request) {
                    String userLine = latestUserLine(request);
                    return new LlmChatResponse("assistant: " + userLine.substring("USER:".length()).strip(), "stop",
                            Map.of("apiKey", apiKey));
                }

                @Override
                public void streamChat(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey,
                                       LlmChatRequest request, Consumer<LlmStreamEvent> eventConsumer) {
                    eventConsumer.accept(LlmStreamEvent.done("stop"));
                }

                private String latestUserLine(LlmChatRequest request) {
                    String userLine = "USER: Hello";
                    for (var message : request.messages()) {
                        userLine = message.content().lines()
                                .filter(line -> line.startsWith("USER:"))
                                .reduce((first, second) -> second)
                                .orElse(userLine);
                    }
                    return userLine;
                }
            }));
        }
    }
}
