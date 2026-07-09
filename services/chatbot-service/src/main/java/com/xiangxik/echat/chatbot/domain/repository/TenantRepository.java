package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.Tenant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantId(String tenantId);

    boolean existsByTenantId(String tenantId);

    List<Tenant> findAllByOrderByTenantIdAsc();
}