package com.xiangxik.echat.chatbot.api;

import com.xiangxik.echat.chatbot.api.admin.AdminAuthController;
import com.xiangxik.echat.chatbot.api.admin.AdminTokenVerifier;
import com.xiangxik.echat.chatbot.api.dto.AdminLoginRequest;
import com.xiangxik.echat.chatbot.config.ChatbotProperties;
import com.xiangxik.echat.chatbot.service.AdminIdentityService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminAuthControllerCookieTest {

    @Test
    void secureFlagFollowsAdminCookieSecureProperty() {
        AdminAuthController controller = new AdminAuthController(properties(true), new AdminTokenVerifier(),
            mock(AdminIdentityService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.login(new AdminLoginRequest("test-admin-token"), response);

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains(AdminAuthController.ADMIN_SESSION_COOKIE)
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Secure");
    }

    private ChatbotProperties properties(boolean adminCookieSecure) {
        return new ChatbotProperties(
                new ChatbotProperties.Service("0.1.0"),
                new ChatbotProperties.Llm("openai-compatible", "gpt-test", "http://localhost", "", 0.2, 1024),
                new ChatbotProperties.Context(12000, 20, 1536),
            new ChatbotProperties.Security("test-only-change-this-32-byte-secret", "test-admin-token",
                List.of("http://localhost:*"), 1000, 1000, adminCookieSecure, List.of()));
    }
}