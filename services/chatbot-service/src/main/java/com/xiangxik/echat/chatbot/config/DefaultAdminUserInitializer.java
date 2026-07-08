package com.xiangxik.echat.chatbot.config;

import com.xiangxik.echat.chatbot.domain.model.AdminRole;
import com.xiangxik.echat.chatbot.domain.model.AdminUser;
import com.xiangxik.echat.chatbot.domain.repository.AdminRoleRepository;
import com.xiangxik.echat.chatbot.domain.repository.AdminUserRepository;
import java.util.Optional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class DefaultAdminUserInitializer implements ApplicationRunner {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final AdminUserRepository userRepository;
    private final AdminRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChatbotProperties properties;

    public DefaultAdminUserInitializer(AdminUserRepository userRepository, AdminRoleRepository roleRepository,
                                       PasswordEncoder passwordEncoder, ChatbotProperties properties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AdminRole superAdminRole = roleRepository.findByCode(SUPER_ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role must exist before seeding the default admin user"));
        AdminUser admin = userRepository.findByUsername(DEFAULT_ADMIN_USERNAME).orElseGet(() -> {
            AdminUser created = new AdminUser();
            created.setUsername(DEFAULT_ADMIN_USERNAME);
            return created;
        });

        admin.setDisplayName(defaultDisplayName());
        admin.setTenantId(defaultTenantId());
        admin.setEnabled(true);
        admin.setSystemUser(true);
        if (!StringUtils.hasText(admin.getPasswordHash())
                || !passwordEncoder.matches(properties.security().adminToken(), admin.getPasswordHash())) {
            admin.setPasswordHash(passwordEncoder.encode(properties.security().adminToken()));
        }
        admin.getRoles().add(superAdminRole);
        userRepository.save(admin);
    }

    private String defaultDisplayName() {
        return firstConfiguredPrincipal()
                .map(ChatbotProperties.AdminPrincipalProperties::displayName)
                .filter(StringUtils::hasText)
                .orElse("Default Admin");
    }

    private String defaultTenantId() {
        return firstConfiguredPrincipal()
                .map(ChatbotProperties.AdminPrincipalProperties::tenantId)
                .filter(StringUtils::hasText)
                .orElse("default");
    }

    private Optional<ChatbotProperties.AdminPrincipalProperties> firstConfiguredPrincipal() {
        return properties.security().adminPrincipals().stream().findFirst();
    }
}
