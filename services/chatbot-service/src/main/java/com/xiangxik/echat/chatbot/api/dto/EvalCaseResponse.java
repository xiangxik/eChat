package com.xiangxik.echat.chatbot.api.dto;

import java.util.List;
import java.util.Map;

public record EvalCaseResponse(
        Long id,
        Long datasetId,
        String input,
        String expectedBehavior,
        List<String> expectedKeywords,
        Map<String, Object> metadata
) {
}
