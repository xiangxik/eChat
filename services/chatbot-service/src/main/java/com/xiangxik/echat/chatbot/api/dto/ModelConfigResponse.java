package com.xiangxik.echat.chatbot.api.dto;

import com.xiangxik.echat.chatbot.domain.model.ModelType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Model configuration response")
public record ModelConfigResponse(
        Long id,
        String tenantId,
        Long providerId,
        String providerName,
        String displayName,
        String modelName,
        ModelType modelType,
        Integer maxContextTokens,
        Double defaultTemperature,
        Double defaultTopP,
        boolean supportsStreaming,
        boolean enabled,
        Map<String, Object> metadata
) {
}