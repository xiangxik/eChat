package com.xiangxik.echat.chatbot.service.context;

import java.util.List;

public class ContextDslException extends IllegalArgumentException {

    private final List<ContextDslError> errors;

    public ContextDslException(List<ContextDslError> errors) {
        super(errors.isEmpty() ? "Invalid context policy DSL" : errors.getFirst().reason());
        this.errors = List.copyOf(errors);
    }

    public List<ContextDslError> getErrors() {
        return errors;
    }
}