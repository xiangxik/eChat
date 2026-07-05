package com.xiangxik.echat.chatbot.api.dto;

import com.xiangxik.echat.chatbot.domain.model.MemoryScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(description = "Memory item create/update request")
public record MemoryItemRequest(
        @NotNull Long chatbotId,
        @Size(max = 128) String userId,
        MemoryScope scope,
        @NotBlank @Size(max = 8000) String content,
        Map<String, Object> metadata
) {
}