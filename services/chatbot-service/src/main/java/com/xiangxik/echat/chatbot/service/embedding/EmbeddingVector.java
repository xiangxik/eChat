package com.xiangxik.echat.chatbot.service.embedding;

public record EmbeddingVector(float[] values) {

    public EmbeddingVector {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Embedding vector must not be empty");
        }
        values = values.clone();
    }

    @Override
    public float[] values() {
        return values.clone();
    }

    public int dimension() {
        return values.length;
    }
}