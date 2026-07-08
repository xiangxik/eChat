package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(String username, @NotBlank String password) {

    public AdminLoginRequest(String password) {
        this(null, password);
    }
}