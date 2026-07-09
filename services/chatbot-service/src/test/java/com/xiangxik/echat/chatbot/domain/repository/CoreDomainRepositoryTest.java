package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.PostgresIntegrationTest;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ChatbotWorkflowNode;
import com.xiangxik.echat.chatbot.domain.model.Conversation;
import com.xiangxik.echat.chatbot.domain.model.ConversationStatus;
import com.xiangxik.echat.chatbot.domain.model.MemoryItem;
import com.xiangxik.echat.chatbot.domain.model.MemoryScope;
import com.xiangxik.echat.chatbot.domain.model.Message;
import com.xiangxik.echat.chatbot.domain.model.MessageRole;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ModelType;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoreDomainRepositoryTest extends PostgresIntegrationTest {

    private static final String TENANT_ID = "tenant-a";

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
    private MessageRepository messageRepository;

    @Autowired
    private MemoryItemRepository memoryItemRepository;

    @Test
    void persistsCoreChatbotGraphAndQueryMethods() {
        String suffix = UUID.randomUUID().toString();
        ProviderConfig provider = new ProviderConfig();
        provider.setTenantId(TENANT_ID);
        provider.setName("Anthropic " + suffix);
        provider.setType(ProviderType.ANTHROPIC);
        provider.setBaseUrl("https://api.anthropic.com");
        provider = providerConfigRepository.save(provider);

        ModelConfig model = new ModelConfig();
        model.setProvider(provider);
        model.setDisplayName("Claude Sonnet");
        model.setModelName("claude-sonnet-4");
        model.setModelType(ModelType.CHAT);
        model.setMaxContextTokens(200000);
        model.setDefaultTemperature(0.2);
        model.setDefaultTopP(0.9);
        model.setMetadata(Map.of("tier", "production"));
        model = modelConfigRepository.save(model);

        ChatbotConfig chatbot = new ChatbotConfig();
        chatbot.setTenantId(TENANT_ID);
        chatbot.setName("Support Bot " + suffix);
        chatbot = chatbotConfigRepository.save(chatbot);

        ChatbotWorkflowNode node = new ChatbotWorkflowNode();
        node.setChatbot(chatbot);
        node.setNodeKey("start");
        node.setName("Start");
        node.setDslContent("""
                <contextPolicy name="repository-test" maxTokens="12000">
                    <system priority="100">Repository test assistant.</system>
                    <output>
                        <section name="system" />
                    </output>
                </contextPolicy>
                """);
        node.setVersion(1);
        node.setModel(model);
        node.setStart(true);
        node.setEnabled(true);
        workflowNodeRepository.save(node);

        Conversation conversation = new Conversation();
        conversation.setTenantId(TENANT_ID);
        conversation.setChatbot(chatbot);
        conversation.setCurrentWorkflowNode(node);
        conversation.setUserId("user-1");
        conversation.setTitle("Billing question");
        conversation = conversationRepository.save(conversation);

        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(MessageRole.USER);
        message.setContent("How do I update my billing email?");
        message.setTokenCount(9);
        message = messageRepository.save(message);

        MemoryItem memoryItem = new MemoryItem();
        memoryItem.setTenantId(TENANT_ID);
        memoryItem.setChatbot(chatbot);
        memoryItem.setUserId("user-1");
        memoryItem.setScope(MemoryScope.LONG_TERM);
        memoryItem.setContent("User prefers concise answers.");
        memoryItem = memoryItemRepository.save(memoryItem);

        assertThat(modelConfigRepository.findByProviderIdAndModelName(provider.getId(), "claude-sonnet-4"))
                .contains(model);
        assertThat(chatbotConfigRepository.findByTenantIdAndEnabledTrueOrderByNameAsc(TENANT_ID)).contains(chatbot);
        assertThat(conversationRepository.findByTenantIdAndChatbotIdAndStatusOrderByUpdatedAtDesc(TENANT_ID, chatbot.getId(), ConversationStatus.ACTIVE))
                .contains(conversation);
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())).containsExactly(message);
        assertThat(memoryItemRepository.findByTenantIdAndChatbotIdAndScopeOrderByUpdatedAtDesc(TENANT_ID, chatbot.getId(), MemoryScope.LONG_TERM))
                .contains(memoryItem);
    }
}