package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop100ByOrderByOccurredAtDesc();

    List<AuditLog> findTop100ByTenantIdOrderByOccurredAtDesc(String tenantId);
}