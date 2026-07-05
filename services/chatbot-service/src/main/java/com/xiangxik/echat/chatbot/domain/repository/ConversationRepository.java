package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.Conversation;
import com.xiangxik.echat.chatbot.domain.model.ConversationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByChatbotIdAndStatusOrderByUpdatedAtDesc(Long chatbotId, ConversationStatus status);

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<Conversation> findByAnonymousSessionIdOrderByUpdatedAtDesc(String anonymousSessionId);
}