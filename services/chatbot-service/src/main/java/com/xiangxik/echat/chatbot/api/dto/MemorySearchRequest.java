package com.xiangxik.echat.chatbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Memory vector search request")
public record MemorySearchRequest(
        @NotNull Long chatbotId,
        @Size(max = 128) String userId,
        @NotBlank @Size(max = 8000) String query,
        @Positive Integer topK,
        @DecimalMin("0.0") Double minScore
) {
}