package com.xiangxik.echat.chatbot.service;

public record RuntimeRequestContext(
        String requestId,
        String traceId,
        String remoteAddress
) {
}