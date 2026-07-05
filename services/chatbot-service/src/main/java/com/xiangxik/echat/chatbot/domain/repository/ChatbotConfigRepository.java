package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotConfigRepository extends JpaRepository<ChatbotConfig, Long> {

    Optional<ChatbotConfig> findByName(String name);

    List<ChatbotConfig> findByEnabledTrueOrderByNameAsc();
}