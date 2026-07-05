package com.xiangxik.echat.chatbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangxik.echat.chatbot.service.context.ContextMemoryItem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ShortTermMemoryCache {

    private static final Logger log = LoggerFactory.getLogger(ShortTermMemoryCache.class);
    private static final Duration TTL = Duration.ofHours(6);
    private static final int MAX_ITEMS = 20;
    private static final TypeReference<List<ContextMemoryItem>> ITEMS_TYPE = new TypeReference<>() {
    };

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;

    public ShortTermMemoryCache(ObjectProvider<StringRedisTemplate> redisTemplateProvider, ObjectMapper objectMapper) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
    }

    public List<ContextMemoryItem> list(Long conversationId, int limit) {
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return List.of();
            }
            String json = redisTemplate.opsForValue().get(key(conversationId));
            if (json == null || json.isBlank()) {
                return List.of();
            }
            List<ContextMemoryItem> items = objectMapper.readValue(json, ITEMS_TYPE);
            int fromIndex = Math.max(0, items.size() - Math.max(1, limit));
            return List.copyOf(items.subList(fromIndex, items.size()));
        } catch (Exception ex) {
            log.debug("Redis short-term memory cache unavailable for conversationId={}", conversationId, ex);
            return List.of();
        }
    }

    public void append(Long conversationId, ContextMemoryItem item) {
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null) {
                return;
            }
            List<ContextMemoryItem> items = new ArrayList<>(list(conversationId, MAX_ITEMS));
            items.add(item);
            if (items.size() > MAX_ITEMS) {
                items = new ArrayList<>(items.subList(items.size() - MAX_ITEMS, items.size()));
            }
            redisTemplate.opsForValue().set(key(conversationId), objectMapper.writeValueAsString(items), TTL);
        } catch (Exception ex) {
            log.debug("Unable to append short-term memory for conversationId={}", conversationId, ex);
        }
    }

    private String key(Long conversationId) {
        return "echat:chat:short-term-memory:" + conversationId;
    }
}