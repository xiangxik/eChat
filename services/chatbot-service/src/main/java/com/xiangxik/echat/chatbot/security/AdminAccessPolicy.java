package com.xiangxik.echat.chatbot.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminAccessPolicy {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    public boolean isAuthorized(HttpServletRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> roles = roles(authentication);
        if (!tenantAllowed(request, authentication, roles)) {
            return false;
        }
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/admin/auth/")) {
            return true;
        }
        if (path.startsWith("/api/admin/audit-logs")) {
            return "GET".equals(method) && hasAny(roles, "SUPER_ADMIN", "ADMIN", "AUDITOR");
        }
        if ("GET".equals(method)) {
            return hasAny(roles, "SUPER_ADMIN", "ADMIN", "AUDITOR", "VIEWER");
        }
        return hasAny(roles, "SUPER_ADMIN", "ADMIN");
    }

    private boolean tenantAllowed(HttpServletRequest request, Authentication authentication, Set<String> roles) {
        if (roles.contains("SUPER_ADMIN")) {
            return true;
        }
        String requestedTenant = request.getHeader(TENANT_HEADER);
        if (!StringUtils.hasText(requestedTenant)) {
            requestedTenant = request.getParameter("tenantId");
        }
        if (!StringUtils.hasText(requestedTenant)) {
            return true;
        }
        return authentication.getPrincipal() instanceof AdminPrincipal principal
                && requestedTenant.strip().equals(principal.tenantId());
    }

    private Set<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring("ROLE_".length()) : authority)
                .map(role -> role.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean hasAny(Set<String> roles, String... requiredRoles) {
        for (String requiredRole : requiredRoles) {
            if (roles.contains(requiredRole)) {
                return true;
            }
        }
        return false;
    }
}