package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ChatbotWorkflowRequest(
        @NotNull List<@Valid ChatbotWorkflowNodeRequest> nodes,
        @NotNull List<@Valid ChatbotWorkflowTransitionRequest> transitions
) {
}