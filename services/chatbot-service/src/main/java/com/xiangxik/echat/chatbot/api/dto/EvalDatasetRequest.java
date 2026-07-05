package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EvalDatasetRequest(
        @NotBlank @Size(max = 180) String name,
        @Size(max = 4000) String description,
        @NotNull Long chatbotId
) {
}
