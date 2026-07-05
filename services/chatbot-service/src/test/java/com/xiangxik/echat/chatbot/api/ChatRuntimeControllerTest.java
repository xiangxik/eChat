package com.xiangxik.echat.chatbot.api;

import com.xiangxik.echat.chatbot.PostgresIntegrationTest;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ContextPolicy;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ModelType;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ContextPolicyRepository;
import com.xiangxik.echat.chatbot.domain.repository.MessageRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ChatRuntimeControllerTest extends PostgresIntegrationTest {

    private static final String API_KEY = "sk-runtime-secret";

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
    private ChatbotConfigRepository chatbotConfigRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ApiKeyProtector apiKeyProtector;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void createsConversationSendsMessageAndPersistsMessages() throws Exception {
        Long chatbotId = createConfiguredChatbot();

        String conversationJson = mockMvc.perform(post("/api/chat/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatbotId":%d,
                                  "userId":"user-1",
                                  "title":"Runtime smoke"
                                }
                                """.formatted(chatbotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chatbotId").value(chatbotId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long conversationId = JsonTestSupport.id(conversationJson);

        mockMvc.perform(post("/api/chat/conversations/{id}/messages", conversationId)
                        .header("X-Request-Id", "runtime-request-1")
                        .header("X-Trace-Id", "runtime-trace-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":"Hello",
                                  "metadata":{"apiKey":"should-not-leak","topic":"smoke"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("runtime-request-1"))
                .andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.assistantMessage.content").value("assistant: Hello"))
                .andExpect(jsonPath("$.assistantMessage.metadata.tokenBudgetReport.totalEstimatedTokens").exists())
                .andExpect(content().string(containsString("[FILTERED]")));

        mockMvc.perform(get("/api/chat/conversations/{id}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"));

        org.assertj.core.api.Assertions.assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .hasSize(2);
    }

        @Test
        void createsConversationWithInitialMessageAndReturnsAssistantMessage() throws Exception {
        Long chatbotId = createConfiguredChatbot();

        String conversationJson = mockMvc.perform(post("/api/chat/conversations")
                .header("X-Request-Id", "runtime-create-message-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "chatbotId":%d,
                      "message":"Hello from create",
                      "metadata":{"topic":"create"}
                    }
                    """.formatted(chatbotId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.chatbotId").value(chatbotId))
            .andExpect(jsonPath("$.requestId").value("runtime-create-message-1"))
            .andExpect(jsonPath("$.assistantMessage.content").value("assistant: Hello from create"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        Long conversationId = JsonTestSupport.id(conversationJson);

        org.assertj.core.api.Assertions.assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
            .extracting(message -> message.getRole().name())
            .containsExactly("USER", "ASSISTANT");
        }

    @Test
    void streamsAssistantResponseAsSseAndPersistsAssistantMessage() throws Exception {
        Long chatbotId = createConfiguredChatbot();
        Long conversationId = createConversation(chatbotId);

        MvcResult result = mockMvc.perform(post("/api/chat/conversations/{id}/stream", conversationId)
                        .header("X-Request-Id", "runtime-stream-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":"Stream please",
                                  "metadata":{}
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:message_start")))
                .andExpect(content().string(containsString("event:token")))
                .andExpect(content().string(containsString("event:message_delta")))
                .andExpect(content().string(containsString("event:message_end")))
                .andExpect(content().string(containsString("assistant: Stream please")));

        org.assertj.core.api.Assertions.assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .extracting(message -> message.getRole().name())
                .containsExactly("USER", "ASSISTANT");
    }

    @Test
    void returnsClearErrorWhenProviderModelIsNotConfigured() throws Exception {
        ChatbotConfig chatbot = new ChatbotConfig();
        chatbot.setName("Runtime no model " + UUID.randomUUID());
        chatbot.setEnabled(true);
        chatbot = chatbotConfigRepository.saveAndFlush(chatbot);

        Long conversationId = createConversation(chatbot.getId());

        mockMvc.perform(post("/api/chat/conversations/{id}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":"Hello",
                                  "metadata":{}
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_NOT_CONFIGURED"));
    }

    private Long createConfiguredChatbot() {
        String suffix = UUID.randomUUID().toString();
        ProviderConfig provider = new ProviderConfig();
        provider.setName("Runtime OpenAI " + suffix);
        provider.setType(ProviderType.OPENAI_COMPATIBLE);
        provider.setBaseUrl("http://localhost:8081/v1");
        provider.setEncryptedApiKey(apiKeyProtector.encrypt(API_KEY));
        provider.setEnabled(true);
        provider = providerConfigRepository.saveAndFlush(provider);

        ModelConfig model = new ModelConfig();
        model.setProvider(provider);
        model.setDisplayName("Runtime GPT " + suffix);
        model.setModelName("gpt-runtime");
        model.setModelType(ModelType.CHAT);
        model.setMaxContextTokens(8192);
        model.setDefaultTemperature(0.2);
        model.setDefaultTopP(0.9);
        model.setSupportsStreaming(true);
        model.setEnabled(true);
        model = modelConfigRepository.saveAndFlush(model);

                ContextPolicy policy = new ContextPolicy();
                policy.setName("Runtime policy " + suffix);
                policy.setDslContent(runtimePolicyDsl());
                policy.setVersion(1);
                policy.setModel(model);
                policy.setEnabled(true);
                policy = contextPolicyRepository.saveAndFlush(policy);

        ChatbotConfig chatbot = new ChatbotConfig();
        chatbot.setName("Runtime bot " + suffix);
                chatbot.setContextPolicy(policy);
        chatbot.setEnabled(true);
        return chatbotConfigRepository.saveAndFlush(chatbot).getId();
    }

        private String runtimePolicyDsl() {
                return """
                                <contextPolicy name="runtime-test" maxTokens="12000">
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

    private Long createConversation(Long chatbotId) throws Exception {
        String response = mockMvc.perform(post("/api/chat/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chatbotId":%d,
                                  "anonymousSessionId":"anon-runtime"
                                }
                                """.formatted(chatbotId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonTestSupport.id(response);
    }

    @TestConfiguration
    static class RuntimeLlmClientConfiguration {

        @Bean
        @Primary
        LlmProviderClientRegistry runtimeClientRegistry() {
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
                    String userLine = latestUserLine(request);
                    for (String token : List.of("assistant: ", userLine.substring("USER:".length()).strip())) {
                        eventConsumer.accept(LlmStreamEvent.token(token, token));
                    }
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