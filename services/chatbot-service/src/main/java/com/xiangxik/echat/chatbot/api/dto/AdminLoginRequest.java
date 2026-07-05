package com.xiangxik.echat.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(@NotBlank String password) {
}