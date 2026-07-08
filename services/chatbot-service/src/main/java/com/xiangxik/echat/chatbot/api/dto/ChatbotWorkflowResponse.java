package com.xiangxik.echat.chatbot.api.dto;

import java.util.List;

public record ChatbotWorkflowResponse(
        Long chatbotId,
        List<ChatbotWorkflowNodeResponse> nodes,
        List<ChatbotWorkflowTransitionResponse> transitions
) {
}