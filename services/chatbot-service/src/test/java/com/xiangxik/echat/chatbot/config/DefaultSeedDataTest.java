package com.xiangxik.echat.chatbot.config;

import com.xiangxik.echat.chatbot.PostgresIntegrationTest;
import com.xiangxik.echat.chatbot.domain.model.AdminRole;
import com.xiangxik.echat.chatbot.domain.model.AdminUser;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.domain.repository.AdminRoleRepository;
import com.xiangxik.echat.chatbot.domain.repository.AdminUserRepository;
import com.xiangxik.echat.chatbot.domain.repository.ModelConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.TenantRepository;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class DefaultSeedDataTest extends PostgresIntegrationTest {

    @Autowired
    private ProviderConfigRepository providerConfigRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private AdminRoleRepository adminRoleRepository;

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedsDefaultProvidersAndAdminBootstrapData() {
        var providersByTenant = providerConfigRepository.findAll().stream()
            .collect(java.util.stream.Collectors.groupingBy(ProviderConfig::getTenantId,
                java.util.stream.Collectors.toMap(ProviderConfig::getName, provider -> provider)));

        assertEquals(Set.of("default", "tenant-a"), providersByTenant.keySet());
        providersByTenant.values().forEach(providersByName -> assertDefaultProviders(providersByName));

        AdminRole superAdminRole = adminRoleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        AdminUser adminUser = adminUserRepository.findByUsername("admin").orElseThrow();
        assertTrue(adminUser.isEnabled());
        assertTrue(adminUser.isSystemUser());
        assertEquals("Test Admin", adminUser.getDisplayName());
        assertEquals("tenant-a", adminUser.getTenantId());
        assertTrue(passwordEncoder.matches("test-admin-token", adminUser.getPasswordHash()));
        assertEquals("SUPER_ADMIN", superAdminRole.getCode());
        assertTrue(superAdminRole.isSystemRole());
        assertEquals(0, modelConfigRepository.count());
        assertTrue(tenantRepository.findByTenantId("default").isPresent());
        assertTrue(tenantRepository.findByTenantId("tenant-a").isPresent());
    }

    private void assertDefaultProviders(Map<String, ProviderConfig> providersByName) {
        assertEquals(Set.of("Minimax Oversea", "Minimax China", "Qwen", "Claude", "OpenAI", "Gemini"), providersByName.keySet());
        assertTrue(providersByName.values().stream().allMatch(provider -> !provider.isEnabled() && provider.getEncryptedApiKey() == null));
        assertEquals(ProviderType.ANTHROPIC, providersByName.get("Minimax Oversea").getType());
        assertEquals("https://api.minimax.io/anthropic/v1", providersByName.get("Minimax Oversea").getBaseUrl());
        assertEquals(ProviderType.OPENAI_COMPATIBLE, providersByName.get("Minimax China").getType());
        assertEquals("https://api.minimax.chat/v1", providersByName.get("Minimax China").getBaseUrl());
    }
}