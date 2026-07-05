package com.xiangxik.echat.chatbot.service.context;

public interface TokenEstimator {

    int estimate(String content);
}