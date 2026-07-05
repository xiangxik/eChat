package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.MemoryItem;
import com.xiangxik.echat.chatbot.domain.model.MemoryScope;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryItemRepository extends JpaRepository<MemoryItem, Long> {

    List<MemoryItem> findByChatbotIdAndScopeOrderByUpdatedAtDesc(Long chatbotId, MemoryScope scope);

    List<MemoryItem> findByChatbotIdAndUserIdOrderByUpdatedAtDesc(Long chatbotId, String userId);
}