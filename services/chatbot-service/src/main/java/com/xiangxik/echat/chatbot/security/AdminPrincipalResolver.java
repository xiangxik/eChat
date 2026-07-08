package com.xiangxik.echat.chatbot.security;

import com.xiangxik.echat.chatbot.api.admin.AdminAuthController;
import com.xiangxik.echat.chatbot.api.admin.AdminTokenInterceptor;
import com.xiangxik.echat.chatbot.api.admin.AdminTokenVerifier;
import com.xiangxik.echat.chatbot.config.ChatbotProperties;
import com.xiangxik.echat.chatbot.service.AdminIdentityService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminPrincipalResolver {

    private static final String LEGACY_ADMIN_TENANT = "default";

    private final ChatbotProperties properties;
    private final AdminTokenVerifier adminTokenVerifier;
    private final AdminIdentityService adminIdentityService;

    public AdminPrincipalResolver(ChatbotProperties properties, AdminTokenVerifier adminTokenVerifier,
                                  AdminIdentityService adminIdentityService) {
        this.properties = properties;
        this.adminTokenVerifier = adminTokenVerifier;
        this.adminIdentityService = adminIdentityService;
    }

    public Optional<AdminPrincipal> resolve(HttpServletRequest request) {
        String headerToken = request.getHeader(AdminTokenInterceptor.ADMIN_TOKEN_HEADER);
        String cookieToken = findSessionCookie(request);
        Optional<AdminPrincipal> userPrincipal = adminIdentityService.resolveSessionToken(cookieToken);
        if (userPrincipal.isPresent()) {
            return userPrincipal;
        }
        for (ChatbotProperties.AdminPrincipalProperties principal : properties.security().adminPrincipals()) {
            if (adminTokenVerifier.matches(principal.token(), headerToken)
                    || adminTokenVerifier.matches(principal.token(), cookieToken)) {
                return Optional.of(toPrincipal(principal));
            }
        }
        if (adminTokenVerifier.matches(properties.security().adminToken(), headerToken)
                || adminTokenVerifier.matches(properties.security().adminToken(), cookieToken)) {
            return Optional.of(new AdminPrincipal("admin", "Legacy Admin", LEGACY_ADMIN_TENANT,
                    Set.of("SUPER_ADMIN", "ADMIN", "AUDITOR"), java.util.Map.of("legacyToken", true)));
        }
        return Optional.empty();
    }

    private AdminPrincipal toPrincipal(ChatbotProperties.AdminPrincipalProperties principal) {
        Set<String> roles = principal.roles().stream()
                .filter(StringUtils::hasText)
                .map(role -> role.strip().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        return new AdminPrincipal(principal.actorId(), principal.displayName(), principal.tenantId(), roles,
                principal.attributes());
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