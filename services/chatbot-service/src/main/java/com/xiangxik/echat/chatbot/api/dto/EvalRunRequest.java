package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record EvalRunRequest(
        @NotNull Long datasetId,
        Long chatbotId,
        Long modelId,
        Long contextPolicyId,
        @Min(1) Integer maxEstimatedTokens,
        List<@Size(max = 240) String> forbiddenPhrases,
        Map<String, Object> metadata
) {
    public EvalRunRequest {
        forbiddenPhrases = forbiddenPhrases == null ? List.of() : List.copyOf(forbiddenPhrases);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
