package com.xiangxik.echat.chatbot.api;

import com.xiangxik.echat.chatbot.api.dto.ChatConversationCreateRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatConversationCreateResponse;
import com.xiangxik.echat.chatbot.api.dto.ChatConversationResponse;
import com.xiangxik.echat.chatbot.api.dto.ChatMessageRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatMessageResponse;
import com.xiangxik.echat.chatbot.api.dto.ChatRuntimeResponse;
import com.xiangxik.echat.chatbot.security.AdminAccessPolicy;
import com.xiangxik.echat.chatbot.service.ChatRuntimeService;
import com.xiangxik.echat.chatbot.service.RuntimeRequestContext;
import com.xiangxik.echat.chatbot.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatRuntimeController {

    private final ChatRuntimeService chatRuntimeService;
    private final TenantService tenantService;

    public ChatRuntimeController(ChatRuntimeService chatRuntimeService, TenantService tenantService) {
        this.chatRuntimeService = chatRuntimeService;
        this.tenantService = tenantService;
    }

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatConversationCreateResponse createConversation(@Valid @RequestBody ChatConversationCreateRequest request,
                                                             @RequestHeader(value = "X-Request-Id", required = false)
                                                             String requestId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false)
                                                             String traceId,
                                                             @RequestHeader(value = AdminAccessPolicy.TENANT_HEADER,
                                                                     required = false) String tenantId,
                                                             HttpServletRequest servletRequest) {
        return chatRuntimeService.createConversation(request, context(requestId, traceId, tenantId, servletRequest));
    }

    @GetMapping("/conversations/{id}")
    public ChatConversationResponse getConversation(@PathVariable Long id,
                                                    @RequestHeader(value = AdminAccessPolicy.TENANT_HEADER,
                                                            required = false) String tenantId,
                                                    HttpServletRequest servletRequest) {
        return chatRuntimeService.getConversation(id, context(null, null, tenantId, servletRequest));
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ChatMessageResponse> listMessages(@PathVariable Long id,
                                                  @RequestHeader(value = AdminAccessPolicy.TENANT_HEADER,
                                                          required = false) String tenantId,
                                                  HttpServletRequest servletRequest) {
        return chatRuntimeService.listMessages(id, context(null, null, tenantId, servletRequest));
    }

    @PostMapping("/conversations/{id}/messages")
    public ChatRuntimeResponse sendMessage(@PathVariable Long id, @Valid @RequestBody ChatMessageRequest request,
                                           @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                           @RequestHeader(value = AdminAccessPolicy.TENANT_HEADER, required = false)
                                           String tenantId,
                                           HttpServletRequest servletRequest) {
        return chatRuntimeService.sendMessage(id, request, context(requestId, traceId, tenantId, servletRequest));
    }

    @PostMapping("/conversations/{id}/stream")
    public SseEmitter streamMessage(@PathVariable Long id, @Valid @RequestBody ChatMessageRequest request,
                                    @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                    @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                    @RequestHeader(value = AdminAccessPolicy.TENANT_HEADER, required = false)
                                    String tenantId,
                                    HttpServletRequest servletRequest) {
        return chatRuntimeService.streamMessage(id, request, context(requestId, traceId, tenantId, servletRequest));
    }

    private RuntimeRequestContext context(String requestId, String traceId, String tenantId, HttpServletRequest request) {
        String resolvedRequestId = hasText(requestId) ? requestId : UUID.randomUUID().toString();
        String resolvedTraceId = hasText(traceId) ? traceId : resolvedRequestId;
        return new RuntimeRequestContext(tenantService.normalizeTenantId(tenantId), resolvedRequestId, resolvedTraceId,
                request.getRemoteAddr());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}