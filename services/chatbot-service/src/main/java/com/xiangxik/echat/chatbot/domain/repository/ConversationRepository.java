package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.Conversation;
import com.xiangxik.echat.chatbot.domain.model.ConversationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @EntityGraph(attributePaths = {"chatbot", "currentWorkflowNode"})
    @Query("select conversation from Conversation conversation where conversation.tenantId = :tenantId and conversation.id = :id")
    Optional<Conversation> findByTenantIdAndIdWithChatbotAndWorkflowNode(@Param("tenantId") String tenantId,
                                                                         @Param("id") Long id);

    List<Conversation> findByTenantIdAndChatbotIdAndStatusOrderByUpdatedAtDesc(String tenantId, Long chatbotId,
                                                                                ConversationStatus status);

    List<Conversation> findByTenantIdAndUserIdOrderByUpdatedAtDesc(String tenantId, String userId);

    List<Conversation> findByTenantIdAndAnonymousSessionIdOrderByUpdatedAtDesc(String tenantId,
                                                                               String anonymousSessionId);
}