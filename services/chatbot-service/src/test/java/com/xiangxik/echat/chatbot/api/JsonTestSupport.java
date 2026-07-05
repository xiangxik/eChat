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
}