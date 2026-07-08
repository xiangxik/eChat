package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import java.util.List;
import java.util.Map;

public record EvalRunRequest(
        @NotNull Long datasetId,
        Long chatbotId,
        Long modelId,
        @Min(1) Integer maxEstimatedTokens,
        @Min(1) Integer maxLatencyMillis,
        @DecimalMin("0.0") Double maxEstimatedCostUsd,
        @DecimalMin("0.0") Double costPer1kTokensUsd,
        Boolean goldenReplay,
        List<@Size(max = 240) String> forbiddenPhrases,
        Map<String, Object> rubric,
        Map<String, Object> releaseGate,
        Map<String, Object> metadata
) {
    public EvalRunRequest {
        forbiddenPhrases = forbiddenPhrases == null ? List.of() : List.copyOf(forbiddenPhrases);
        rubric = rubric == null ? Map.of() : Map.copyOf(rubric);
        releaseGate = releaseGate == null ? Map.of() : Map.copyOf(releaseGate);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
