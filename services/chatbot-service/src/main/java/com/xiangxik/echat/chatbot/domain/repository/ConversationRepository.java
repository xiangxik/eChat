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
    @Query("select conversation from Conversation conversation where conversation.id = :id")
    Optional<Conversation> findByIdWithChatbotAndWorkflowNode(@Param("id") Long id);

    List<Conversation> findByChatbotIdAndStatusOrderByUpdatedAtDesc(Long chatbotId, ConversationStatus status);

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<Conversation> findByAnonymousSessionIdOrderByUpdatedAtDesc(String anonymousSessionId);
}