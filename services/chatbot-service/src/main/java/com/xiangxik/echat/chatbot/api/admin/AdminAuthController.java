package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.api.dto.AdminLoginRequest;
import com.xiangxik.echat.chatbot.api.dto.AdminSessionResponse;
import com.xiangxik.echat.chatbot.config.ChatbotProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
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

    public AdminAuthController(ChatbotProperties properties, AdminTokenVerifier adminTokenVerifier) {
        this.properties = properties;
        this.adminTokenVerifier = adminTokenVerifier;
    }

    @PostMapping("/login")
    @Operation(summary = "Start an admin session")
    public AdminSessionResponse login(@Valid @RequestBody AdminLoginRequest request, HttpServletResponse response) {
        String password = request.password().trim();
        if (!adminTokenVerifier.matches(properties.security().adminToken(), password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid admin credentials");
        }

        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(password, SESSION_MAX_AGE).toString());
        return new AdminSessionResponse(true);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "End the current admin session")
    public void logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString());
    }

    @GetMapping("/session")
    @Operation(summary = "Read the current admin session")
    public AdminSessionResponse session() {
        return new AdminSessionResponse(true);
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(ADMIN_SESSION_COOKIE, value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}