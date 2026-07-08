package com.xiangxik.echat.chatbot.config;

import com.xiangxik.echat.chatbot.domain.model.AdminRole;
import com.xiangxik.echat.chatbot.domain.model.AdminUser;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.domain.repository.AdminRoleRepository;
import com.xiangxik.echat.chatbot.domain.repository.AdminUserRepository;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultDataInitializer implements ApplicationRunner {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    private final ChatbotProperties properties;
    private final ProviderConfigRepository providerConfigRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public DefaultDataInitializer(ChatbotProperties properties,
                                  ProviderConfigRepository providerConfigRepository,
                                  AdminUserRepository adminUserRepository,
                                  AdminRoleRepository adminRoleRepository,
                                  PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.providerConfigRepository = providerConfigRepository;
        this.adminUserRepository = adminUserRepository;
        this.adminRoleRepository = adminRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        initializeDefaults();
    }

    public void initializeDefaults() {
        seedProviders();
        seedSystemAdmin();
    }

    private void seedProviders() {
        for (ChatbotProperties.ProviderSeedProperties seed : properties.bootstrap().providers()) {
            if (providerConfigRepository.findByName(seed.name()).isPresent()) {
                continue;
            }
            ProviderConfig provider = new ProviderConfig();
            provider.setName(seed.name());
            provider.setType(ProviderType.valueOf(seed.type().name()));
            provider.setBaseUrl(seed.baseUrl());
            provider.setEnabled(false);
            providerConfigRepository.save(provider);
        }
    }

    private void seedSystemAdmin() {
        AdminRole superAdminRole = adminRoleRepository.findByCode("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role is required before seeding admin user"));

        ChatbotProperties.AdminPrincipalProperties configuredPrincipal = configuredAdminPrincipal();
        AdminUser adminUser = adminUserRepository.findByUsername(DEFAULT_ADMIN_USERNAME)
                .orElseGet(AdminUser::new);
        adminUser.setUsername(DEFAULT_ADMIN_USERNAME);
        adminUser.setDisplayName(resolveDisplayName(configuredPrincipal));
        adminUser.setTenantId(resolveTenantId(configuredPrincipal));
        adminUser.setPasswordHash(passwordEncoder.encode(properties.security().adminToken()));
        adminUser.setEnabled(true);
        adminUser.setSystemUser(true);
        adminUser.setRoles(new LinkedHashSet<>(List.of(superAdminRole)));
        adminUserRepository.save(adminUser);
    }

    private ChatbotProperties.AdminPrincipalProperties configuredAdminPrincipal() {
        return properties.security().adminPrincipals().stream()
                .filter(principal -> properties.security().adminToken().equals(principal.token()))
                .findFirst()
                .or(() -> properties.security().adminPrincipals().stream().findFirst())
                .orElse(null);
    }

    private String resolveDisplayName(ChatbotProperties.AdminPrincipalProperties configuredPrincipal) {
        if (configuredPrincipal != null && StringUtils.hasText(configuredPrincipal.displayName())) {
            return configuredPrincipal.displayName().trim();
        }
        return "Admin";
    }

    private String resolveTenantId(ChatbotProperties.AdminPrincipalProperties configuredPrincipal) {
        if (configuredPrincipal != null && StringUtils.hasText(configuredPrincipal.tenantId())) {
            return configuredPrincipal.tenantId().trim();
        }
        return "default";
    }
}