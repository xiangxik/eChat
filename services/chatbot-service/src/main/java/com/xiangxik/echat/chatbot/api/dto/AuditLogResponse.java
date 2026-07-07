package com.xiangxik.echat.chatbot.api.dto;

import java.time.Instant;
import java.util.Map;

public record AuditLogResponse(
        Long id,
        Instant occurredAt,
        String actorType,
        String actorId,
        String tenantId,
        String eventType,
        String resourceType,
        String resourceId,
        String requestId,
        String traceId,
        String remoteAddress,
        Map<String, Object> metadata
) {
}