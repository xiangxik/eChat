package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.api.dto.AdminLoginRequest;
import com.xiangxik.echat.chatbot.api.dto.AdminLoginResult;
import com.xiangxik.echat.chatbot.api.dto.AdminSessionResponse;
import com.xiangxik.echat.chatbot.config.ChatbotProperties;
import com.xiangxik.echat.chatbot.security.AdminPrincipal;
import com.xiangxik.echat.chatbot.service.AdminIdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Admin Auth", description = "Authenticate admin web sessions")
public class AdminAuthController {

    public static final String ADMIN_SESSION_COOKIE = "echat_admin_session";

    private static final Duration SESSION_MAX_AGE = Duration.ofHours(12);

    private final ChatbotProperties properties;
    private final AdminTokenVerifier adminTokenVerifier;
    private final AdminIdentityService adminIdentityService;

    public AdminAuthController(ChatbotProperties properties, AdminTokenVerifier adminTokenVerifier,
                               AdminIdentityService adminIdentityService) {
        this.properties = properties;
        this.adminTokenVerifier = adminTokenVerifier;
        this.adminIdentityService = adminIdentityService;
    }

    @PostMapping("/login")
    @Operation(summary = "Start an admin session")
    public AdminSessionResponse login(@Valid @RequestBody AdminLoginRequest request, HttpServletResponse response) {
        String password = request.password().trim();
        Optional<AdminLoginResult> userLogin = adminIdentityService.authenticate(request.username(), password);
        if (userLogin.isPresent()) {
            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(userLogin.get().sessionToken(), SESSION_MAX_AGE).toString());
            return AdminSessionResponse.basicAuthenticated();
        }
        if (!adminTokenVerifier.matches(properties.security().adminToken(), password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid admin credentials");
        }

        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(password, SESSION_MAX_AGE).toString());
        return AdminSessionResponse.basicAuthenticated();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "End the current admin session")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        adminIdentityService.logout(findSessionCookie(request));
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
    }

    @GetMapping("/session")
    @Operation(summary = "Read the current admin session")
    public AdminSessionResponse session() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal) {
            return AdminSessionResponse.authenticated(principal.actorId(), principal.displayName(), principal.tenantId(),
                    principal.roles());
        }
        return AdminSessionResponse.basicAuthenticated();
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(ADMIN_SESSION_COOKIE, value)
                .httpOnly(true)
                .secure(properties.security().adminCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private String findSessionCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (ADMIN_SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}