package com.xiangxik.echat.chatbot.api;

import com.xiangxik.echat.chatbot.PostgresIntegrationTest;
import com.xiangxik.echat.chatbot.security.RateLimitFilter;
import com.xiangxik.echat.chatbot.security.RequestCorrelationFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = "echat.security.admin-rate-limit-per-minute=1")
class SecurityHardeningControllerTest extends PostgresIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestCorrelationFilter requestCorrelationFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestCorrelationFilter, rateLimitFilter, springSecurityFilterChain)
                .build();
    }

    @Test
    void adminApiIsProtectedBySpringSecurityFilterChain() throws Exception {
        mockMvc.perform(get("/api/admin/providers"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/json")));
    }

    @Test
    void adminRateLimitReturnsTooManyRequests() throws Exception {
        mockMvc.perform(get("/api/admin/providers")
                        .header("X-Forwarded-For", "203.0.113.10"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/providers")
                        .header("X-Forwarded-For", "203.0.113.10"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void requestCorrelationHeadersAreReturned() throws Exception {
        mockMvc.perform(get("/api/health").header("X-Request-Id", "test-request-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "test-request-id"))
                .andExpect(header().exists("X-Trace-Id"));
    }
}