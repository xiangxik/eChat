package com.xiangxik.echat.chatbot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(REQUEST_ID_HEADER));
        String traceId = resolve(request.getHeader(TRACE_ID_HEADER), requestId);
        MDC.put("requestId", requestId);
        MDC.put("traceId", traceId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            MDC.remove("traceId");
        }
    }

    private String resolve(String value) {
        return resolve(value, UUID.randomUUID().toString());
    }

    private String resolve(String value, String fallback) {
        if (!StringUtils.hasText(value) || value.length() > 128) {
            return fallback;
        }
        return value;
    }
}