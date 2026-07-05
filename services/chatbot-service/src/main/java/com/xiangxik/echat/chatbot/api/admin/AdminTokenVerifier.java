package com.xiangxik.echat.chatbot.api.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AdminTokenVerifier {

    public boolean matches(String expectedToken, String actualToken) {
        if (!StringUtils.hasText(expectedToken) || !StringUtils.hasText(actualToken)) {
            return false;
        }

        byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = actualToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}