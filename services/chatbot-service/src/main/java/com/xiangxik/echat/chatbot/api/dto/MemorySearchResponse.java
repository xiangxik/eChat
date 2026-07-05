package com.xiangxik.echat.chatbot.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Memory vector search result")
public record MemorySearchResponse(
        Long id,
        String content,
        double score,
        Map<String, Object> metadata
) {
}