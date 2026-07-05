package com.xiangxik.echat.chatbot.api.dto;

import com.xiangxik.echat.chatbot.domain.model.ModelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(description = "Model configuration create/update request")
public record ModelConfigRequest(
        @NotNull Long providerId,
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @Size(max = 200) String modelName,
        @NotNull ModelType modelType,
        @Positive Integer maxContextTokens,
        @DecimalMin("0.0") @DecimalMax("2.0") Double defaultTemperature,
        @DecimalMin("0.0") @DecimalMax("1.0") Double defaultTopP,
        Boolean supportsStreaming,
        Boolean enabled,
        Map<String, Object> metadata
) {
}