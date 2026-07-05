package com.xiangxik.echat.chatbot.service.context;

import java.util.List;
import java.util.Map;

public record TokenBudgetReport(
        int maxTokens,
        Map<String, Integer> reservedBySection,
        Map<String, Integer> actualTokensBySection,
        int totalEstimatedTokens,
        List<String> truncatedSections
) {
}