package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.ChatbotWorkflowTransition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatbotWorkflowTransitionRepository extends JpaRepository<ChatbotWorkflowTransition, Long> {

    List<ChatbotWorkflowTransition> findByChatbotIdOrderByFromNodeNodeKeyAscPriorityAscIdAsc(Long chatbotId);

    List<ChatbotWorkflowTransition> findByChatbotIdAndFromNodeIdAndEnabledTrueOrderByPriorityAscIdAsc(Long chatbotId,
                                                                                                       Long fromNodeId);
}