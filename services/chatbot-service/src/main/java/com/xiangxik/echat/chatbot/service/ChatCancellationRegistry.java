package com.xiangxik.echat.chatbot.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class ChatCancellationRegistry {

    private final ConcurrentMap<String, AtomicBoolean> requests = new ConcurrentHashMap<>();

    public void register(String requestId) {
        requests.put(requestId, new AtomicBoolean(false));
    }

    public void cancel(String requestId) {
        AtomicBoolean cancelled = requests.get(requestId);
        if (cancelled != null) {
            cancelled.set(true);
        }
    }

    public boolean isCancelled(String requestId) {
        AtomicBoolean cancelled = requests.get(requestId);
        return cancelled != null && cancelled.get();
    }

    public void complete(String requestId) {
        requests.remove(requestId);
    }
}