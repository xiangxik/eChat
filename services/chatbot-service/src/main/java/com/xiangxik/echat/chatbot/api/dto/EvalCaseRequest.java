package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record EvalCaseRequest(
        @NotBlank @Size(max = 8000) String input,
        @Size(max = 4000) String expectedBehavior,
        List<@Size(max = 160) String> expectedKeywords,
        Map<String, Object> metadata
) {
    public EvalCaseRequest {
        expectedKeywords = expectedKeywords == null ? List.of() : List.copyOf(expectedKeywords);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
