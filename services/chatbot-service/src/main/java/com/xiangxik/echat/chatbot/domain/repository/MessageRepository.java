package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<Message> findByConversationTenantIdAndConversationIdOrderByCreatedAtAsc(String tenantId, Long conversationId);
}