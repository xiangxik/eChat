package com.xiangxik.echat.chatbot.service;

public record RuntimeRequestContext(
        String tenantId,
        String requestId,
        String traceId,
        String remoteAddress
) {
}