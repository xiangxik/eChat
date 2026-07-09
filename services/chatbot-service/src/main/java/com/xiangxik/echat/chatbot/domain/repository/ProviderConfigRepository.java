package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderConfigRepository extends JpaRepository<ProviderConfig, Long> {

    Optional<ProviderConfig> findByTenantIdAndId(String tenantId, Long id);

    Optional<ProviderConfig> findByTenantIdAndName(String tenantId, String name);

    List<ProviderConfig> findByTenantIdOrderByNameAsc(String tenantId);

    List<ProviderConfig> findByTenantIdAndEnabledTrueOrderByNameAsc(String tenantId);
}