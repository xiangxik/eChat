package com.xiangxik.echat.chatbot.domain.model;

public enum ModelType {
    CHAT,
    EMBEDDING,
    RERANKER;

    public boolean isChat() {
        return this == CHAT;
    }
}