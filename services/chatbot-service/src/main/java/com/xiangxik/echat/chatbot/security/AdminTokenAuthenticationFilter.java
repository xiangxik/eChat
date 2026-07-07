package com.xiangxik.echat.chatbot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AdminPrincipalResolver adminPrincipalResolver;
    private final AdminAuthenticationFactory adminAuthenticationFactory;

    public AdminTokenAuthenticationFilter(AdminPrincipalResolver adminPrincipalResolver,
                                          AdminAuthenticationFactory adminAuthenticationFactory) {
        this.adminPrincipalResolver = adminPrincipalResolver;
        this.adminAuthenticationFactory = adminAuthenticationFactory;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isAdminRequest(request)) {
            adminPrincipalResolver.resolve(request).ifPresent(principal -> SecurityContextHolder.getContext()
                    .setAuthentication(adminAuthenticationFactory.create(principal)));
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAdminRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith(request.getContextPath() + "/api/admin/")
                && !path.equals(request.getContextPath() + "/api/admin/auth/login")
                && !path.equals(request.getContextPath() + "/api/admin/auth/logout");
    }

}