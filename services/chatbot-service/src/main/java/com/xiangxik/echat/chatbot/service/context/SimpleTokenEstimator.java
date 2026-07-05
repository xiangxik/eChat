package com.xiangxik.echat.chatbot.service.context;

import org.springframework.stereotype.Component;

@Component
public class SimpleTokenEstimator implements TokenEstimator {

    @Override
    public int estimate(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.strip().length() / 4.0));
    }
}