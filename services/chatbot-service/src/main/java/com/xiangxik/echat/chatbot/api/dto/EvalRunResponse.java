package com.xiangxik.echat.chatbot.api.dto;

import java.time.Instant;
import java.util.Map;

public record EvalRunResponse(
        Long id,
        Long datasetId,
        Long chatbotId,
        Long modelId,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Map<String, Object> summary
) {
}
