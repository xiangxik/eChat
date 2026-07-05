package com.xiangxik.echat.chatbot.service;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatSessionStateCache {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionStateCache.class);
    private static final Duration STREAM_STATE_TTL = Duration.ofMinutes(15);

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public ChatSessionStateCache(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public void recordStreamState(String requestId, String state) {
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set("echat:chat:stream:" + requestId, state, STREAM_STATE_TTL);
            }
        } catch (Exception ex) {
            log.debug("Redis stream state cache unavailable for requestId={}", requestId, ex);
        }
    }
}