package com.xiangxik.echat.chatbot.api;

import com.xiangxik.echat.chatbot.PostgresIntegrationTest;
import com.xiangxik.echat.chatbot.api.dto.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class HealthControllerTest extends PostgresIntegrationTest {

    @Autowired
    private HealthController healthController;

    @Test
    void returnsHealthStatus() {
        HealthResponse response = healthController.health();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.service()).isEqualTo("chatbot-service");
        assertThat(response.version()).isEqualTo("0.1.0");
    }
}