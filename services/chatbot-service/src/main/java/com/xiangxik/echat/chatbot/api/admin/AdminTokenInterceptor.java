package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.config.ChatbotProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final ChatbotProperties properties;
    private final AdminTokenVerifier adminTokenVerifier;

    public AdminTokenInterceptor(ChatbotProperties properties, AdminTokenVerifier adminTokenVerifier) {
        this.properties = properties;
        this.adminTokenVerifier = adminTokenVerifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String expectedToken = properties.security().adminToken();
        String headerToken = request.getHeader(ADMIN_TOKEN_HEADER);
        String cookieToken = findSessionCookie(request);
        if (adminTokenVerifier.matches(expectedToken, headerToken) || adminTokenVerifier.matches(expectedToken, cookieToken)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid admin token\"}");
        return false;
    }

    private String findSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AdminAuthController.ADMIN_SESSION_COOKIE.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}