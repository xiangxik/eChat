package com.xiangxik.echat.chatbot.service;

import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

public record AdminListQuery(Map<String, String> params) {

    public static final String SEARCH = "search";
    public static final String SORT_FIELD = "sortField";
    public static final String SORT_ORDER = "sortOrder";

    public static AdminListQuery empty() {
        return new AdminListQuery(Map.of());
    }

    public static AdminListQuery from(Map<String, String> params) {
        return new AdminListQuery(params == null ? Map.of() : Map.copyOf(params));
    }

    public String search() {
        return value(SEARCH);
    }

    public String sortField() {
        return value(SORT_FIELD);
    }

    public boolean descending() {
        String sortOrder = value(SORT_ORDER);
        return "descend".equalsIgnoreCase(sortOrder) || "desc".equalsIgnoreCase(sortOrder);
    }

    public String value(String key) {
        String value = params.get(key);
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    public boolean hasValue(String key) {
        return value(key) != null;
    }

    public Boolean booleanValue(String key) {
        String value = value(key);
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "enabled", "stored", "system", "passed" -> Boolean.TRUE;
            case "false", "0", "no", "disabled", "not_set", "not-set", "custom", "failed" -> Boolean.FALSE;
            default -> null;
        };
    }
}