package com.xiangxik.echat.chatbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Standard API error response")
public record ApiErrorResponse(
        @Schema(example = "2026-07-05T12:00:00Z") Instant timestamp,
        @Schema(example = "VALIDATION_FAILED") String code,
        @Schema(example = "Request validation failed") String message,
        @Schema(example = "/api/admin/providers") String path,
        List<FieldViolation> violations
) {

    public record FieldViolation(String field, String message) {
    }
}