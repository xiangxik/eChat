package com.xiangxik.echat.chatbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangxik.echat.chatbot.api.dto.ChatConversationCreateRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatConversationCreateResponse;
import com.xiangxik.echat.chatbot.api.dto.ChatConversationResponse;
import com.xiangxik.echat.chatbot.api.dto.ChatMessageRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatMessageResponse;
import com.xiangxik.echat.chatbot.api.dto.ChatRuntimeResponse;
import com.xiangxik.echat.chatbot.api.dto.ChatStreamEventResponse;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ContextPolicy;
import com.xiangxik.echat.chatbot.domain.model.Conversation;
import com.xiangxik.echat.chatbot.domain.model.Message;
import com.xiangxik.echat.chatbot.domain.model.MessageRole;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.service.context.ContextAssemblyRequest;
import com.xiangxik.echat.chatbot.service.context.ContextAssemblyResult;
import com.xiangxik.echat.chatbot.service.context.ContextEngine;
import com.xiangxik.echat.chatbot.service.context.ContextMessage;
import com.xiangxik.echat.chatbot.service.context.ContextPolicyDefinition;
import com.xiangxik.echat.chatbot.service.context.ContextPolicyValidator;
import com.xiangxik.echat.chatbot.service.context.TokenBudgetReport;
import com.xiangxik.echat.chatbot.service.context.TokenEstimator;
import com.xiangxik.echat.chatbot.service.llm.LlmChatMessage;
import com.xiangxik.echat.chatbot.service.llm.LlmChatRequest;
import com.xiangxik.echat.chatbot.service.llm.LlmChatResponse;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClient;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClientRegistry;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(ChatRuntimeService.class);
    private static final int MAX_MESSAGE_LENGTH = 8000;
    private static final long SSE_TIMEOUT_MILLIS = 90_000L;

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ContextPolicyValidator contextPolicyValidator;
    private final ContextEngine contextEngine;
    private final LlmProviderClientRegistry clientRegistry;
    private final ApiKeyProtector apiKeyProtector;
    private final TokenEstimator tokenEstimator;
    private final ObjectMapper objectMapper;
    private final ChatSessionStateCache sessionStateCache;
    private final ChatCancellationRegistry cancellationRegistry;
    private final TransactionTemplate transactionTemplate;

    public ChatRuntimeService(ConversationService conversationService,
                              MessageService messageService,
                              ContextPolicyValidator contextPolicyValidator,
                              ContextEngine contextEngine,
                              LlmProviderClientRegistry clientRegistry,
                              ApiKeyProtector apiKeyProtector,
                              TokenEstimator tokenEstimator,
                              ObjectMapper objectMapper,
                              ChatSessionStateCache sessionStateCache,
                              ChatCancellationRegistry cancellationRegistry,
                              TransactionTemplate transactionTemplate) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.contextPolicyValidator = contextPolicyValidator;
        this.contextEngine = contextEngine;
        this.clientRegistry = clientRegistry;
        this.apiKeyProtector = apiKeyProtector;
        this.tokenEstimator = tokenEstimator;
        this.objectMapper = objectMapper;
        this.sessionStateCache = sessionStateCache;
        this.cancellationRegistry = cancellationRegistry;
        this.transactionTemplate = transactionTemplate;
    }

    public ChatConversationCreateResponse createConversation(ChatConversationCreateRequest request,
                                                             RuntimeRequestContext context) {
        Map<String, Object> metadata = sanitizeMetadata(request.metadata());
        Conversation conversation = conversationService.create(request.chatbotId(), request.userId(),
                request.anonymousSessionId(), request.title());
        log.info("audit.chat.conversation.created requestId={} traceId={} conversationId={} chatbotId={} userIdPresent={} remoteAddress={}",
                context.requestId(), context.traceId(), conversation.getId(), request.chatbotId(),
                StringUtils.hasText(request.userId()), context.remoteAddress());
        if (!metadata.isEmpty()) {
            log.debug("conversation metadata accepted requestId={} keys={}", context.requestId(), metadata.keySet());
        }
        ChatConversationResponse conversationResponse = toConversationResponse(conversation);
        if (StringUtils.hasText(request.message())) {
            ChatRuntimeResponse runtimeResponse = sendMessage(conversation.getId(),
                    new ChatMessageRequest(request.message(), metadata), context);
            return ChatConversationCreateResponse.withInitialMessage(runtimeResponse);
        }
        return ChatConversationCreateResponse.withoutInitialMessage(conversationResponse, context.requestId(),
                context.traceId());
    }

    public ChatConversationResponse getConversation(Long id) {
        return toConversationResponse(conversationService.get(id));
    }

    public List<ChatMessageResponse> listMessages(Long conversationId) {
        conversationService.get(conversationId);
        return messageService.listByConversation(conversationId).stream().map(this::toMessageResponse).toList();
    }

    public ChatRuntimeResponse sendMessage(Long conversationId, ChatMessageRequest request,
                                           RuntimeRequestContext context) {
        PreparedInvocation invocation = prepareInvocation(conversationId, request, context);
        LlmProviderClient client = clientRegistry.getClient(invocation.provider().getType());
        try {
            LlmChatResponse response = client.chat(invocation.provider(), invocation.model(), invocation.apiKey(),
                    invocation.llmRequest());
            Message assistantMessage = saveAssistantMessage(conversationId, response.content(), context,
                    invocation.contextResult(), response.finishReason(), response.metadata());
            log.info("audit.chat.message.completed requestId={} traceId={} conversationId={} userMessageId={} assistantMessageId={} tokens={}",
                    context.requestId(), context.traceId(), conversationId, invocation.userMessage().getId(),
                    assistantMessage.getId(), assistantMessage.getTokenCount());
            return new ChatRuntimeResponse(context.requestId(), context.traceId(), invocation.conversationResponse(),
                    toMessageResponse(invocation.userMessage()), toMessageResponse(assistantMessage),
                    invocation.contextResult().tokenBudgetReport(), invocation.contextResult().warnings());
        } catch (LlmProviderException ex) {
            log.warn("audit.chat.message.failed requestId={} traceId={} conversationId={} reason={}",
                    context.requestId(), context.traceId(), conversationId, ex.getMessage());
            throw ex;
        }
    }

    public SseEmitter streamMessage(Long conversationId, ChatMessageRequest request,
                                    RuntimeRequestContext context) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        cancellationRegistry.register(context.requestId());
        sessionStateCache.recordStreamState(context.requestId(), "starting");
        CompletableFuture.runAsync(() -> streamInBackground(conversationId, request, context, emitter));
        return emitter;
    }

    private void streamInBackground(Long conversationId, ChatMessageRequest request, RuntimeRequestContext context,
                                    SseEmitter emitter) {
        StringBuilder assistantContent = new StringBuilder();
        try {
            PreparedInvocation invocation = prepareInvocation(conversationId, request, context);
            LlmProviderClient client = clientRegistry.getClient(invocation.provider().getType());
            sessionStateCache.recordStreamState(context.requestId(), "streaming");
            send(emitter, "message_start", new ChatStreamEventResponse("message_start", context.requestId(),
                    context.traceId(), conversationId, null, null, null, null,
                    invocation.contextResult().tokenBudgetReport(), Map.of("warnings", invocation.contextResult().warnings())));

            client.streamChat(invocation.provider(), invocation.model(), invocation.apiKey(), invocation.llmRequest(), event -> {
                if (cancellationRegistry.isCancelled(context.requestId())) {
                    throw new ChatRuntimeException("CHAT_CANCELLED", "Chat request was cancelled", HttpStatus.CONFLICT);
                }
                if (StringUtils.hasText(event.token())) {
                    assistantContent.append(event.token());
                    send(emitter, "token", new ChatStreamEventResponse("token", context.requestId(), context.traceId(),
                            conversationId, null, event.token(), null, null, null, Map.of()));
                    send(emitter, "message_delta", new ChatStreamEventResponse("message_delta", context.requestId(),
                            context.traceId(), conversationId, null, null, assistantContent.toString(), null, null,
                            Map.of()));
                }
            });

            Message assistantMessage = saveAssistantMessage(conversationId, assistantContent.toString(), context,
                    invocation.contextResult(), "stop", Map.of("stream", true));
            sessionStateCache.recordStreamState(context.requestId(), "completed");
            send(emitter, "message_end", new ChatStreamEventResponse("message_end", context.requestId(),
                    context.traceId(), conversationId, assistantMessage.getId(), null, assistantContent.toString(),
                    toMessageResponse(assistantMessage), invocation.contextResult().tokenBudgetReport(), Map.of()));
            log.info("audit.chat.stream.completed requestId={} traceId={} conversationId={} userMessageId={} assistantMessageId={} tokens={}",
                    context.requestId(), context.traceId(), conversationId, invocation.userMessage().getId(),
                    assistantMessage.getId(), assistantMessage.getTokenCount());
            emitter.complete();
        } catch (Exception ex) {
            sessionStateCache.recordStreamState(context.requestId(), "failed");
            log.warn("audit.chat.stream.failed requestId={} traceId={} conversationId={} reason={}",
                    context.requestId(), context.traceId(), conversationId, ex.getMessage());
            try {
                send(emitter, "error", new ChatStreamEventResponse("error", context.requestId(), context.traceId(),
                        conversationId, null, null, null, null, null, Map.of("message", safeErrorMessage(ex))));
            } finally {
                emitter.complete();
            }
        } finally {
            cancellationRegistry.complete(context.requestId());
        }
    }

    private PreparedInvocation prepareInvocation(Long conversationId, ChatMessageRequest request,
                                                 RuntimeRequestContext context) {
        String message = validateMessage(request.message());
        Map<String, Object> metadata = sanitizeMetadata(request.metadata());
        return transactionTemplate.execute(status -> {
            Conversation conversation = conversationService.get(conversationId);
            ChatbotConfig chatbot = conversation.getChatbot();
            validateChatbot(chatbot);
            ModelConfig model = chatbot.getDefaultModel();
            validateModel(model);
            ProviderConfig provider = model.getProvider();
            validateProvider(provider);
            String apiKey = apiKeyProtector.decrypt(provider.getEncryptedApiKey());

            Message userMessage = messageService.create(conversationId, MessageRole.USER, message,
                    tokenEstimator.estimate(message), requestMetadata(metadata, context));
            ContextAssemblyResult contextResult = assembleContext(conversation, message, metadata, context);
            LlmChatRequest llmRequest = new LlmChatRequest(contextResult.messages().stream()
                    .map(contextMessage -> new LlmChatMessage(toProviderRole(contextMessage.role()),
                            contextMessage.content(), contextMessage.metadata()))
                    .toList(), metadata, context.requestId(), context.traceId(), Duration.ofSeconds(60));
            log.info("audit.chat.message.received requestId={} traceId={} conversationId={} userMessageId={} contextTokens={}",
                    context.requestId(), context.traceId(), conversationId, userMessage.getId(),
                    contextResult.tokenBudgetReport().totalEstimatedTokens());
            return new PreparedInvocation(toConversationResponse(conversation), userMessage, provider, model, apiKey,
                    llmRequest, contextResult);
        });
    }

    private ContextAssemblyResult assembleContext(Conversation conversation, String latestUserMessage,
                                                  Map<String, Object> metadata, RuntimeRequestContext requestContext) {
        List<Message> messages = messageService.listByConversation(conversation.getId());
        ContextPolicyDefinition policy = resolvePolicy(conversation.getChatbot());
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("requestId", requestContext.requestId());
        runtime.put("traceId", requestContext.traceId());
        runtime.put("timestamp", Instant.now().toString());
        runtime.put("cancellationSupported", true);
        return contextEngine.assemble(policy, new ContextAssemblyRequest(conversation.getChatbot().getId(),
                conversation.getId(), conversation.getUserId(), latestUserMessage, metadata,
                messages.stream().map(this::toContextMessage).toList(), List.of(), List.of(), Map.of(), List.of(),
                List.of(), runtime));
    }

    private ContextPolicyDefinition resolvePolicy(ChatbotConfig chatbot) {
        ContextPolicy contextPolicy = chatbot.getContextPolicy();
        if (contextPolicy == null || !contextPolicy.isEnabled()) {
            return contextPolicyValidator.validateAndParse(defaultPolicy());
        }
        return contextPolicyValidator.validateAndParse(contextPolicy.getDslContent());
    }

    private Message saveAssistantMessage(Long conversationId, String content, RuntimeRequestContext context,
                                         ContextAssemblyResult contextResult, String finishReason,
                                         Map<String, Object> providerMetadata) {
        if (!StringUtils.hasText(content)) {
            throw new LlmProviderException("Provider returned an empty assistant message");
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", context.requestId());
        metadata.put("traceId", context.traceId());
        metadata.put("finishReason", finishReason);
        metadata.put("tokenBudgetReport", tokenBudgetReportMap(contextResult.tokenBudgetReport()));
        metadata.put("contextWarnings", contextResult.warnings());
        metadata.put("provider", sanitizeMetadata(providerMetadata));
        return messageService.create(conversationId, MessageRole.ASSISTANT, content, tokenEstimator.estimate(content),
                metadata);
    }

    private void validateChatbot(ChatbotConfig chatbot) {
        if (chatbot == null || !chatbot.isEnabled()) {
            throw new ChatRuntimeException("CHATBOT_UNAVAILABLE", "Chatbot is not enabled", HttpStatus.CONFLICT);
        }
    }

    private void validateModel(ModelConfig model) {
        if (model == null) {
            throw new ChatRuntimeException("MODEL_NOT_CONFIGURED", "Chatbot has no default model configured",
                    HttpStatus.CONFLICT);
        }
        if (!model.isEnabled()) {
            throw new ChatRuntimeException("MODEL_DISABLED", "Chatbot default model is disabled", HttpStatus.CONFLICT);
        }
    }

    private void validateProvider(ProviderConfig provider) {
        if (provider == null) {
            throw new ChatRuntimeException("PROVIDER_NOT_CONFIGURED", "Model has no provider configured",
                    HttpStatus.CONFLICT);
        }
        if (!provider.isEnabled()) {
            throw new ChatRuntimeException("PROVIDER_DISABLED", "Model provider is disabled", HttpStatus.CONFLICT);
        }
    }

    private String validateMessage(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.strip();
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must be at most " + MAX_MESSAGE_LENGTH + " characters");
        }
        boolean hasUnsafeControlCharacter = message.chars()
                .anyMatch(value -> Character.isISOControl(value) && !Character.isWhitespace(value));
        if (hasUnsafeControlCharacter) {
            throw new IllegalArgumentException("message contains unsupported control characters");
        }
        return message;
    }

    private Map<String, Object> requestMetadata(Map<String, Object> metadata, RuntimeRequestContext context) {
        Map<String, Object> result = new LinkedHashMap<>(metadata);
        result.put("requestId", context.requestId());
        result.put("traceId", context.traceId());
        return result;
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (normalized.contains("apikey") || normalized.contains("api_key") || normalized.contains("secret")
                    || normalized.contains("password") || normalized.contains("token")) {
                sanitized.put(key, "[FILTERED]");
            } else {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private String toProviderRole(String role) {
        return Objects.toString(role, "user").toLowerCase(Locale.ROOT);
    }

    private ContextMessage toContextMessage(Message message) {
        return new ContextMessage(message.getRole().name(), message.getContent(), Map.of("messageId", message.getId()));
    }

    private ChatConversationResponse toConversationResponse(Conversation conversation) {
        return new ChatConversationResponse(conversation.getId(), conversation.getChatbot().getId(), conversation.getTitle(),
                conversation.getStatus().name(), conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private ChatMessageResponse toMessageResponse(Message message) {
        return new ChatMessageResponse(message.getId(), message.getConversation().getId(), message.getRole().name(),
                message.getContent(), message.getTokenCount(), message.getMetadata(), message.getCreatedAt());
    }

    private Map<String, Object> tokenBudgetReportMap(TokenBudgetReport tokenBudgetReport) {
        return objectMapper.convertValue(tokenBudgetReport, new TypeReference<>() {
        });
    }

    private String safeErrorMessage(Exception ex) {
        if (ex instanceof ChatRuntimeException runtimeException) {
            return runtimeException.getMessage();
        }
        if (ex instanceof LlmProviderException) {
            return ex.getMessage();
        }
        return "Chat request failed";
    }

    private void send(SseEmitter emitter, String eventName, ChatStreamEventResponse response) {
        try {
            emitter.send(SseEmitter.event().name(eventName).id(response.requestId()).data(response));
        } catch (IOException ex) {
            throw new ChatRuntimeException("STREAM_WRITE_FAILED", "Unable to write SSE event", HttpStatus.CONFLICT);
        }
    }

    private String defaultPolicy() {
        return """
                <contextPolicy name=\"default-runtime\" maxTokens=\"12000\">
                  <system priority=\"100\">You are a helpful enterprise chatbot assistant.</system>
                  <variables>
                    <var name=\"conversation\" source=\"conversation.messages\" maxMessages=\"20\" />
                    <var name=\"context\" source=\"context\" optional=\"true\" />
                    <var name=\"shortTermMemory\" source=\"memory.shortTerm\" topK=\"8\" optional=\"true\" />
                    <var name=\"longTermMemory\" source=\"memory.longTerm\" topK=\"8\" optional=\"true\" />
                    <var name=\"userProfile\" source=\"user.profile\" optional=\"true\" />
                    <var name=\"retrievalResults\" source=\"retrieval.results\" topK=\"6\" optional=\"true\" />
                    <var name=\"toolResults\" source=\"tool.results\" topK=\"6\" optional=\"true\" />
                    <var name=\"runtime\" source=\"runtime\" optional=\"true\" />
                    <var name=\"metadata\" source=\"metadata\" optional=\"true\" />
                  </variables>
                  <budget>
                    <reserve target=\"conversation\" tokens=\"7000\" />
                  </budget>
                  <rules>
                    <truncate target=\"conversation\" strategy=\"oldest-first\" />
                    <exclude target=\"metadata\" when=\"metadata.sensitive == true\" />
                  </rules>
                  <output>
                    <section name=\"system\" />
                    <section name=\"conversation\" />
                    <section name=\"shortTermMemory\" optional=\"true\" />
                    <section name=\"longTermMemory\" optional=\"true\" />
                    <section name=\"userProfile\" optional=\"true\" />
                    <section name=\"retrievalResults\" optional=\"true\" />
                    <section name=\"toolResults\" optional=\"true\" />
                    <section name=\"runtime\" optional=\"true\" />
                    <section name=\"metadata\" optional=\"true\" />
                  </output>
                </contextPolicy>
                """;
    }

    private record PreparedInvocation(
            ChatConversationResponse conversationResponse,
            Message userMessage,
            ProviderConfig provider,
            ModelConfig model,
            String apiKey,
            LlmChatRequest llmRequest,
            ContextAssemblyResult contextResult
    ) {
    }
}