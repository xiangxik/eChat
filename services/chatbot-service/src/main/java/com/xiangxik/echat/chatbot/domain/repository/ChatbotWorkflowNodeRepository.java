package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.ChatbotWorkflowNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotWorkflowNodeRepository extends JpaRepository<ChatbotWorkflowNode, Long> {

    List<ChatbotWorkflowNode> findByChatbotIdOrderByNodeKeyAsc(Long chatbotId);

    Optional<ChatbotWorkflowNode> findByChatbotIdAndStartTrueAndEnabledTrue(Long chatbotId);
}