package com.xiangxik.echat.chatbot.service.context;

import com.xiangxik.echat.chatbot.domain.model.Message;
import com.xiangxik.echat.chatbot.service.MemoryService;
import com.xiangxik.echat.chatbot.service.ShortTermMemoryCache;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderException;
import com.xiangxik.echat.chatbot.service.retrieval.RetrievalProvider;
import com.xiangxik.echat.chatbot.service.retrieval.RetrievalRequest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ContextMemoryResolver {

    private static final Logger log = LoggerFactory.getLogger(ContextMemoryResolver.class);

    private static final int DEFAULT_SHORT_TERM_LIMIT = 6;
    private static final int DEFAULT_LONG_TERM_TOP_K = 8;
    private static final int DEFAULT_RETRIEVAL_TOP_K = 6;

    private final ShortTermMemoryCache shortTermMemoryCache;
    private final MemoryService memoryService;
    private final List<RetrievalProvider> retrievalProviders;

    public ContextMemoryResolver(ShortTermMemoryCache shortTermMemoryCache, MemoryService memoryService,
                                 List<RetrievalProvider> retrievalProviders) {
        this.shortTermMemoryCache = shortTermMemoryCache;
        this.memoryService = memoryService;
        this.retrievalProviders = List.copyOf(retrievalProviders);
    }

    public ContextMemoryBundle resolve(ContextPolicyDefinition policy, Long chatbotId, Long conversationId,
                                       String userId, String latestUserMessage, Map<String, Object> metadata,
                                       List<Message> conversationMessages) {
        ContextPolicyDefinition.VariableDefinition shortTerm = variable(policy, "shortTermMemory");
        ContextPolicyDefinition.VariableDefinition longTerm = variable(policy, "longTermMemory");
        ContextPolicyDefinition.VariableDefinition retrieval = variable(policy, "retrievalResults");

        List<ContextMemoryItem> shortTermMemory = resolveShortTerm(conversationId, conversationMessages,
                limit(shortTerm, DEFAULT_SHORT_TERM_LIMIT));
        List<ContextMemoryItem> longTermMemory = longTerm == null ? List.of()
                : resolveLongTerm(chatbotId, userId, latestUserMessage,
                limit(longTerm, DEFAULT_LONG_TERM_TOP_K), longTerm.minScore());
        List<ContextMemoryItem> retrievalResults = retrieval == null ? List.of()
                : retrieve(chatbotId, conversationId, userId, latestUserMessage, metadata,
                limit(retrieval, DEFAULT_RETRIEVAL_TOP_K), retrieval.minScore());

        return new ContextMemoryBundle(shortTermMemory, longTermMemory, retrievalResults);
    }

    private List<ContextMemoryItem> resolveLongTerm(Long chatbotId, String userId, String query, int topK,
                                                    double minScore) {
        try {
            return memoryService.searchLongTerm(chatbotId, userId, query, topK, minScore);
        } catch (IllegalArgumentException | LlmProviderException ex) {
            log.warn("Long-term memory search skipped for chatbotId={} because vector memory is unavailable: {}",
                    chatbotId, ex.getMessage());
            return List.of();
        }
    }

    private List<ContextMemoryItem> resolveShortTerm(Long conversationId, List<Message> conversationMessages,
                                                     int limit) {
        List<ContextMemoryItem> cached = shortTermMemoryCache.list(conversationId, limit);
        if (!cached.isEmpty()) {
            return cached;
        }
        int fromIndex = Math.max(0, conversationMessages.size() - limit);
        return conversationMessages.subList(fromIndex, conversationMessages.size()).stream()
                .map(message -> new ContextMemoryItem(message.getRole().name() + ": " + abbreviate(message.getContent()),
                        1.0, Map.of("source", "recent-message", "messageId", message.getId())))
                .toList();
    }

    private List<ContextMemoryItem> retrieve(Long chatbotId, Long conversationId, String userId, String query,
                                             Map<String, Object> metadata, int topK, double minScore) {
        if (query == null || query.isBlank() || retrievalProviders.isEmpty()) {
            return List.of();
        }
        RetrievalRequest request = new RetrievalRequest(chatbotId, conversationId, userId, query, topK, minScore,
                metadata == null ? Map.of() : new LinkedHashMap<>(metadata));
        return retrievalProviders.stream()
                .flatMap(provider -> provider.retrieve(request).stream())
                .filter(item -> minScore <= 0 || item.score() >= minScore)
                .sorted(Comparator.comparingDouble(ContextMemoryItem::score).reversed())
                .limit(topK)
                .toList();
    }

    private ContextPolicyDefinition.VariableDefinition variable(ContextPolicyDefinition policy, String name) {
        return policy.variables().stream()
                .filter(variable -> name.equals(variable.name()))
                .findFirst()
                .orElse(null);
    }

    private int limit(ContextPolicyDefinition.VariableDefinition variable, int fallback) {
        if (variable == null) {
            return fallback;
        }
        if (variable.topK() > 0) {
            return variable.topK();
        }
        if (variable.limit() > 0) {
            return variable.limit();
        }
        if (variable.maxMessages() > 0) {
            return variable.maxMessages();
        }
        return fallback;
    }

    private String abbreviate(String content) {
        if (content == null || content.length() <= 300) {
            return content;
        }
        return content.substring(0, 300) + "...";
    }
}