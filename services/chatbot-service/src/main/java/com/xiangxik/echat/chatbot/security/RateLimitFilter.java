package com.xiangxik.echat.chatbot.security;

import com.xiangxik.echat.chatbot.config.ChatbotProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ChatbotProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Map<String, AtomicInteger> fallbackCounters = new ConcurrentHashMap<>();

    public RateLimitFilter(ChatbotProperties properties, ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.properties = properties;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        LimitRule rule = ruleFor(request);
        if (rule == null || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (increment(rule, clientKey(request)) > rule.limit()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private LimitRule ruleFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (path.startsWith(contextPath + "/api/chat/")) {
            return new LimitRule("chat", properties.security().chatRateLimitPerMinute());
        }
        if (path.startsWith(contextPath + "/api/admin/")) {
            return new LimitRule("admin", properties.security().adminRateLimitPerMinute());
        }
        return null;
    }

    private long increment(LimitRule rule, String clientKey) {
        long window = Instant.now().getEpochSecond() / WINDOW.toSeconds();
        String key = "echat:rate:" + rule.name() + ":" + clientKey + ":" + window;
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate != null) {
                Long count = redisTemplate.opsForValue().increment(key);
                if (count != null && count == 1L) {
                    redisTemplate.expire(key, WINDOW.plusSeconds(5));
                }
                return count == null ? 1L : count;
            }
        } catch (RuntimeException ignored) {
        }
        return fallbackCounters.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record LimitRule(String name, int limit) {
    }
}