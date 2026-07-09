package com.xiangxik.echat.chatbot.config;

import com.xiangxik.echat.chatbot.domain.model.AdminRole;
import com.xiangxik.echat.chatbot.domain.model.AdminUser;
import com.xiangxik.echat.chatbot.domain.repository.AdminRoleRepository;
import com.xiangxik.echat.chatbot.domain.repository.AdminUserRepository;
import com.xiangxik.echat.chatbot.service.TenantService;
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
    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;

    public DefaultDataInitializer(ChatbotProperties properties,
                                  AdminUserRepository adminUserRepository,
                                  AdminRoleRepository adminRoleRepository,
                                  PasswordEncoder passwordEncoder,
                                  TenantService tenantService) {
        this.properties = properties;
        this.adminUserRepository = adminUserRepository;
        this.adminRoleRepository = adminRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantService = tenantService;
    }

    @Override
    public void run(ApplicationArguments args) {
        initializeDefaults();
    }

    public void initializeDefaults() {
        String tenantId = resolveTenantId(configuredAdminPrincipal());
        tenantService.ensureTenant(TenantService.DEFAULT_TENANT_ID, "Default Tenant");
        tenantService.ensureTenant(tenantId, tenantId);
        tenantService.seedTenantProviders(TenantService.DEFAULT_TENANT_ID);
        tenantService.seedTenantProviders(tenantId);
        seedSystemAdmin();
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