package com.xiangxik.echat.chatbot.api.dto;

import com.xiangxik.echat.chatbot.domain.model.ModelType;

public record ModelOptionResponse(
        String displayName,
        String modelName,
        ModelType modelType,
        Integer maxContextTokens,
        Double defaultTemperature,
        Double defaultTopP,
        boolean supportsStreaming
) {
}