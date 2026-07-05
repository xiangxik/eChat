package com.xiangxik.echat.chatbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Provider connection test response")
public record ProviderConnectionTestResponse(
        boolean success,
        String providerType,
        String message,
        Integer statusCode
) {
}