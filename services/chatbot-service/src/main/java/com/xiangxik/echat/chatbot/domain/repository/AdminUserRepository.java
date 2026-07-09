package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.AdminUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByUsername(String username);

    List<AdminUser> findByTenantId(String tenantId);

    boolean existsByUsername(String username);
}
