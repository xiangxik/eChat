package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotConfigRepository extends JpaRepository<ChatbotConfig, Long> {

    Optional<ChatbotConfig> findByTenantIdAndId(String tenantId, Long id);

    Optional<ChatbotConfig> findByTenantIdAndName(String tenantId, String name);

    List<ChatbotConfig> findByTenantIdOrderByNameAsc(String tenantId);

    List<ChatbotConfig> findByTenantIdAndEnabledTrueOrderByNameAsc(String tenantId);
}