package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.security.AdminAccessPolicy;
import com.xiangxik.echat.chatbot.security.AdminPrincipal;
import com.xiangxik.echat.chatbot.security.AdminPrincipalResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
    private static final String INTERCEPTOR_AUTHENTICATED = AdminTokenInterceptor.class.getName() + ".authenticated";

    private final AdminPrincipalResolver adminPrincipalResolver;
    private final AdminAccessPolicy adminAccessPolicy;

    public AdminTokenInterceptor(AdminPrincipalResolver adminPrincipalResolver, AdminAccessPolicy adminAccessPolicy) {
        this.adminPrincipalResolver = adminPrincipalResolver;
        this.adminAccessPolicy = adminAccessPolicy;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Optional<AdminPrincipal> principal = adminPrincipalResolver.resolve(request);
        if (principal.isPresent()) {
            setAuthentication(principal.get());
            request.setAttribute(INTERCEPTOR_AUTHENTICATED, true);
        } else if (SecurityContextHolder.getContext().getAuthentication() != null) {
            SecurityContextHolder.clearContext();
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid admin token\"}");
            return false;
        }
        if (adminAccessPolicy.isAuthorized(request, SecurityContextHolder.getContext().getAuthentication())) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Admin principal is not allowed to access this resource\"}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (Boolean.TRUE.equals(request.getAttribute(INTERCEPTOR_AUTHENTICATED))) {
            SecurityContextHolder.clearContext();
        }
    }

    private void setAuthentication(AdminPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "N/A",
                principal.roles().stream()
                        .map(role -> "ROLE_" + role)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toUnmodifiableSet())));
    }
}