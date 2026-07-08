package com.xiangxik.echat.chatbot.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class JsonTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonTestSupport() {
    }

    static Long id(String json) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            return node.get("id").asLong();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to read id from JSON response", ex);
        }
    }

    static Long idByCode(String json, String code) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            for (JsonNode item : node) {
                if (code.equals(item.get("code").asText())) {
                    return item.get("id").asLong();
                }
            }
            throw new IllegalArgumentException("No item with code " + code);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to read id by code from JSON response", ex);
        }
    }

    static Long idByUsername(String json, String username) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            for (JsonNode item : node) {
                if (username.equals(item.get("username").asText())) {
                    return item.get("id").asLong();
                }
            }
            throw new IllegalArgumentException("No item with username " + username);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to read id by username from JSON response", ex);
        }
    }
}