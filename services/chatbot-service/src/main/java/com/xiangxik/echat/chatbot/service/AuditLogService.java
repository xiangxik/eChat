package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.AuditLogResponse;
import com.xiangxik.echat.chatbot.domain.model.AuditLog;
import com.xiangxik.echat.chatbot.domain.repository.AuditLogRepository;
import com.xiangxik.echat.chatbot.security.AdminPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final TenantService tenantService;

    public AuditLogService(AuditLogRepository auditLogRepository, TenantService tenantService) {
        this.auditLogRepository = auditLogRepository;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> listRecent() {
        return listRecent(AdminListQuery.empty());
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> listRecent(AdminListQuery query) {
        List<AuditLogResponse> auditLogs;
        if (tenantService.currentPrincipalIsSuperAdmin()) {
            auditLogs = auditLogRepository.findTop100ByOrderByOccurredAtDesc().stream().map(this::toResponse).toList();
        } else {
            auditLogs = auditLogRepository.findTop100ByTenantIdOrderByOccurredAtDesc(tenantService.currentTenantId()).stream()
                    .map(this::toResponse)
                    .toList();
        }
        return AdminListQuerySupport.apply(auditLogs, query, auditLog -> matchesListQuery(auditLog, query), auditLogSorters(), "occurredAt");
    }

    private boolean matchesListQuery(AuditLogResponse auditLog, AdminListQuery query) {
        return AdminListQuerySupport.containsAny(query.search(), auditLog.tenantId(), auditLog.eventType(),
                auditLog.resourceType(), auditLog.resourceId(), auditLog.actorType(), auditLog.actorId(),
                auditLog.requestId(), auditLog.traceId(), auditLog.remoteAddress())
                && AdminListQuerySupport.contains(auditLog.tenantId(), query.value("tenantId"))
                && AdminListQuerySupport.contains(auditLog.eventType(), query.value("eventType"))
                && AdminListQuerySupport.contains(auditLog.resourceType(), query.value("resourceType"))
                && AdminListQuerySupport.contains(auditLog.actorId(), query.value("actorId"))
                && AdminListQuerySupport.equalsText(auditLog.actorType(), query.value("actorType"));
    }

    private Map<String, java.util.function.Function<AuditLogResponse, ?>> auditLogSorters() {
        return Map.ofEntries(
                Map.entry("occurredAt", AuditLogResponse::occurredAt),
                Map.entry("tenantId", AuditLogResponse::tenantId),
                Map.entry("eventType", AuditLogResponse::eventType),
                Map.entry("resourceType", AuditLogResponse::resourceType),
                Map.entry("resourceId", AuditLogResponse::resourceId),
                Map.entry("actorType", AuditLogResponse::actorType),
                Map.entry("actorId", AuditLogResponse::actorId),
                Map.entry("requestId", AuditLogResponse::requestId),
                Map.entry("traceId", AuditLogResponse::traceId),
                Map.entry("remoteAddress", AuditLogResponse::remoteAddress)
        );
    }

    @Transactional
    public void recordAdmin(String eventType, String resourceType, Object resourceId, Map<String, Object> metadata) {
        record("ADMIN", currentActorId(), currentTenantId(), eventType, resourceType, resourceId, null, null, null,
                adminMetadata(metadata));
    }

    @Transactional
    public void recordRuntime(String tenantId, String eventType, String resourceType, Object resourceId,
                              String requestId, String traceId, String remoteAddress, Map<String, Object> metadata) {
        record("RUNTIME", "chat", tenantId, eventType, resourceType, resourceId, requestId, traceId, remoteAddress,
                metadata);
    }

    private void record(String actorType, String actorId, String tenantId, String eventType, String resourceType,
                        Object resourceId, String requestId, String traceId, String remoteAddress,
                        Map<String, Object> metadata) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActorType(actorType);
        auditLog.setActorId(actorId);
        auditLog.setTenantId(tenantId);
        auditLog.setEventType(eventType);
        auditLog.setResourceType(resourceType);
        auditLog.setResourceId(resourceId == null ? null : resourceId.toString());
        auditLog.setRequestId(requestId);
        auditLog.setTraceId(traceId);
        auditLog.setRemoteAddress(remoteAddress);
        auditLog.setMetadata(sanitize(metadata));
        auditLogRepository.save(auditLog);
    }

    private String currentActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            return "admin";
        }
        return authentication.getName();
    }

    private String currentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal
                && StringUtils.hasText(principal.tenantId())) {
            return principal.tenantId();
        }
        return "default";
    }

    private Map<String, Object> adminMetadata(Map<String, Object> metadata) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        if (metadata != null) {
            enriched.putAll(metadata);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal) {
            enriched.put("actorDisplayName", principal.displayName());
            enriched.put("actorRoles", principal.roles());
            enriched.put("actorAttributes", principal.attributes());
        }
        return enriched;
    }

    private Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (normalized.contains("apikey") || normalized.contains("api_key") || normalized.contains("secret")
                    || normalized.contains("password") || normalized.contains("token") || normalized.contains("content")) {
                sanitized.put(key, "[FILTERED]");
            } else {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(auditLog.getId(), auditLog.getOccurredAt(), auditLog.getActorType(),
                auditLog.getActorId(), auditLog.getTenantId(), auditLog.getEventType(), auditLog.getResourceType(),
                auditLog.getResourceId(), auditLog.getRequestId(), auditLog.getTraceId(), auditLog.getRemoteAddress(),
                auditLog.getMetadata());
    }
}