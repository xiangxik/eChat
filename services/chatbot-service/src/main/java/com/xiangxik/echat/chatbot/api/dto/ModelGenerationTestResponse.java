package com.xiangxik.echat.chatbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Model generation test response")
public record ModelGenerationTestResponse(
        boolean success,
        String providerType,
        String model,
        String message,
        Integer statusCode,
        String sampleText
) {
}