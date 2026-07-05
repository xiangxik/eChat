package com.xiangxik.echat.chatbot.service.llm;

public record LlmProviderTestResult(
        boolean success,
        String message,
        Integer statusCode,
        String sampleText
) {
}