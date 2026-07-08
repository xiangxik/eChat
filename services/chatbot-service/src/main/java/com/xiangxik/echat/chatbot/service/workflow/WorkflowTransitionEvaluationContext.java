package com.xiangxik.echat.chatbot.service.workflow;

import java.util.Map;

public record WorkflowTransitionEvaluationContext(
        Long chatbotId,
        Long conversationId,
        String currentNodeKey,
        String latestUserMessage,
        String assistantMessage,
        Map<String, Object> metadata,
        Map<String, Object> workflowState
) {
}