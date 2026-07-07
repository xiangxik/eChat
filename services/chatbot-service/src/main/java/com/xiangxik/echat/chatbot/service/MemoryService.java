package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.MemoryItemRequest;
import com.xiangxik.echat.chatbot.api.dto.MemoryItemResponse;
import com.xiangxik.echat.chatbot.api.dto.MemorySearchResponse;
import com.xiangxik.echat.chatbot.config.ChatbotProperties;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.MemoryItem;
import com.xiangxik.echat.chatbot.domain.model.MemoryScope;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ModelType;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.MemoryItemRepository;
import com.xiangxik.echat.chatbot.domain.repository.MemoryItemSearchHit;
import com.xiangxik.echat.chatbot.domain.repository.ModelConfigRepository;
import com.xiangxik.echat.chatbot.service.context.ContextMemoryItem;
import com.xiangxik.echat.chatbot.service.embedding.EmbeddingProviderClient;
import com.xiangxik.echat.chatbot.service.embedding.EmbeddingProviderClientRegistry;
import com.xiangxik.echat.chatbot.service.embedding.EmbeddingVector;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

@Service
public class MemoryService {

    private static final int DEFAULT_TOP_K = 8;
    private static final int MAX_TOP_K = 50;

    private final MemoryItemRepository memoryItemRepository;
    private final ChatbotConfigRepository chatbotConfigRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final ProviderConfigService providerConfigService;
    private final EmbeddingProviderClientRegistry embeddingClientRegistry;
    private final ChatbotProperties properties;

    public MemoryService(MemoryItemRepository memoryItemRepository,
                         ChatbotConfigRepository chatbotConfigRepository,
                         ModelConfigRepository modelConfigRepository,
                         ProviderConfigService providerConfigService,
                         EmbeddingProviderClientRegistry embeddingClientRegistry,
                         ChatbotProperties properties) {
        this.memoryItemRepository = memoryItemRepository;
        this.chatbotConfigRepository = chatbotConfigRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.providerConfigService = providerConfigService;
        this.embeddingClientRegistry = embeddingClientRegistry;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<MemoryItemResponse> list(Long chatbotId, MemoryScope scope, String userId) {
        return memoryItemRepository.findByChatbotIdOrderByUpdatedAtDesc(chatbotId).stream()
                .filter(item -> scope == null || item.getScope() == scope)
                .filter(item -> !StringUtils.hasText(userId) || Objects.equals(userId, item.getUserId()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MemoryItemResponse create(MemoryItemRequest request) {
        ChatbotConfig chatbot = chatbotConfigRepository.findById(request.chatbotId())
                .orElseThrow(() -> new ResourceNotFoundException("ChatbotConfig", request.chatbotId()));
        MemoryItem memoryItem = new MemoryItem();
        memoryItem.setChatbot(chatbot);
        apply(memoryItem, request);
        return toResponse(memoryItemRepository.save(memoryItem));
    }

    @Transactional
    public MemoryItemResponse update(Long id, MemoryItemRequest request) {
        MemoryItem memoryItem = memoryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MemoryItem", id));
        if (!Objects.equals(memoryItem.getChatbot().getId(), request.chatbotId())) {
            ChatbotConfig chatbot = chatbotConfigRepository.findById(request.chatbotId())
                    .orElseThrow(() -> new ResourceNotFoundException("ChatbotConfig", request.chatbotId()));
            memoryItem.setChatbot(chatbot);
        }
        apply(memoryItem, request);
        return toResponse(memoryItemRepository.save(memoryItem));
    }

    @Transactional
    public void delete(Long id) {
        MemoryItem memoryItem = memoryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MemoryItem", id));
        memoryItemRepository.delete(memoryItem);
    }

    @Transactional(readOnly = true)
    public List<MemorySearchResponse> search(Long chatbotId, String userId, String query, Integer topK,
                                             Double minScore) {
        return searchLongTerm(chatbotId, userId, query, topK, minScore).stream()
                .map(item -> new MemorySearchResponse(
                        (Long) item.metadata().get("id"),
                        item.content(),
                        item.score(),
                        item.metadata()))
                .toList();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<ContextMemoryItem> searchLongTerm(Long chatbotId, String userId, String query, Integer topK,
                                                  Double minScore) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        int requestedTopK = normalizeTopK(topK);
        double requestedMinScore = minScore == null ? 0 : minScore;
        EmbeddingVector queryEmbedding = embed(query);
        String vectorLiteral = toVectorLiteral(queryEmbedding.values());
        List<MemoryItemSearchHit> hits = memoryItemRepository.searchLongTermByEmbedding(chatbotId, userId, vectorLiteral,
                requestedMinScore, requestedTopK);
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> scores = hits.stream().collect(Collectors.toMap(MemoryItemSearchHit::getId,
                MemoryItemSearchHit::getScore, (first, second) -> first, LinkedHashMap::new));
        Map<Long, MemoryItem> items = memoryItemRepository.findAllById(scores.keySet()).stream()
                .collect(Collectors.toMap(MemoryItem::getId, Function.identity()));
        return scores.entrySet().stream()
                .map(entry -> toContextMemoryItem(items.get(entry.getKey()), entry.getValue()))
                .filter(Objects::nonNull)
                .toList();
    }

    private void apply(MemoryItem memoryItem, MemoryItemRequest request) {
        MemoryScope scope = request.scope() == null ? MemoryScope.LONG_TERM : request.scope();
        String content = request.content() == null ? "" : request.content().strip();
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content must not be blank");
        }
        memoryItem.setUserId(request.userId());
        memoryItem.setScope(scope);
        memoryItem.setContent(content);
        memoryItem.setMetadata(request.metadata());
        if (scope == MemoryScope.SHORT_TERM) {
            memoryItem.setEmbedding(null);
            memoryItem.setEmbeddingDimension(null);
            return;
        }
        EmbeddingVector embedding = embed(content);
        memoryItem.setEmbedding(embedding.values());
        memoryItem.setEmbeddingDimension(embedding.dimension());
    }

    private EmbeddingVector embed(String input) {
        ModelConfig model = resolveEmbeddingModel();
        int expectedDimension = expectedDimension(model);
        if (expectedDimension != properties.context().embeddingDimension()) {
            throw new IllegalArgumentException("Embedding model dimension " + expectedDimension
                    + " does not match pgvector dimension " + properties.context().embeddingDimension());
        }
        ProviderConfig provider = model.getProvider();
        EmbeddingProviderClient client = embeddingClientRegistry.getClient(provider.getType());
        EmbeddingVector embedding = client.embed(provider, model, providerConfigService.decryptedApiKey(provider), input);
        if (embedding.dimension() != expectedDimension) {
            throw new IllegalArgumentException("Embedding response dimension " + embedding.dimension()
                    + " does not match configured model dimension " + expectedDimension);
        }
        return embedding;
    }

    private ModelConfig resolveEmbeddingModel() {
        return modelConfigRepository.findByEnabledTrueAndModelTypeOrderByDisplayNameAsc(ModelType.EMBEDDING).stream()
                .filter(model -> model.getProvider() != null && model.getProvider().isEnabled())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No enabled EMBEDDING model configured"));
    }

    private int expectedDimension(ModelConfig model) {
        Object value = model.getMetadata().getOrDefault("embeddingDimension", model.getMetadata().get("dimensions"));
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Integer.parseInt(text);
        }
        return properties.context().embeddingDimension();
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private String toVectorLiteral(float[] values) {
        String vector = new StringBuilder("[")
                .append(java.util.stream.IntStream.range(0, values.length)
                        .mapToObj(index -> Float.toString(values[index]))
                        .collect(Collectors.joining(",")))
                .append(']')
                .toString();
        return vector;
    }

    private ContextMemoryItem toContextMemoryItem(MemoryItem item, double score) {
        if (item == null) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(item.getMetadata());
        metadata.put("id", item.getId());
        metadata.put("scope", item.getScope().name());
        metadata.put("userId", item.getUserId());
        metadata.put("embeddingDimension", item.getEmbeddingDimension());
        return new ContextMemoryItem(item.getContent(), score, metadata);
    }

    private MemoryItemResponse toResponse(MemoryItem item) {
        return new MemoryItemResponse(item.getId(), item.getChatbot().getId(), item.getUserId(), item.getScope(),
                item.getContent(), item.getEmbeddingDimension(), item.getMetadata(), item.getCreatedAt(),
                item.getUpdatedAt());
    }
}