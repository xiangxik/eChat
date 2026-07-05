package com.xiangxik.echat.chatbot.api.dto;

import java.util.Map;

public record EvalResultResponse(
        Long id,
        Long runId,
        Long caseId,
        String output,
        Map<String, Object> contextSnapshot,
        Map<String, Object> tokenBudgetReport,
        Map<String, Object> scores,
        boolean passed,
        String error
) {
}
