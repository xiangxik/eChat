package com.xiangxik.echat.chatbot.service.context;

import java.util.List;

public record ContextAssemblyResult(
        List<ContextMessage> messages,
        List<ContextSection> sections,
        TokenBudgetReport tokenBudgetReport,
        List<String> warnings
) {
}