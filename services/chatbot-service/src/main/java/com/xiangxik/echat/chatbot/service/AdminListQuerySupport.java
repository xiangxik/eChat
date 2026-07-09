package com.xiangxik.echat.chatbot.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class AdminListQuerySupport {

    private AdminListQuerySupport() {
    }

    public static <T> List<T> apply(List<T> items, AdminListQuery query, Predicate<T> filter,
                                    Map<String, Function<T, ?>> sorters, String defaultSortField) {
        List<T> result = items.stream().filter(filter).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        String requestedSortField = query.sortField();
        String sortField = requestedSortField != null && sorters.containsKey(requestedSortField)
            ? requestedSortField
            : defaultSortField;
        Function<T, ?> extractor = sorters.get(sortField);
        if (extractor != null) {
            Comparator<T> comparator = (left, right) -> compareValues(extractor.apply(left), extractor.apply(right));
            if (query.descending()) {
                comparator = comparator.reversed();
            }
            result.sort(comparator);
        }
        return result;
    }

    public static boolean containsAny(String needle, Object... values) {
        if (needle == null) {
            return true;
        }
        String normalizedNeedle = normalize(needle);
        for (Object value : values) {
            if (value != null && normalize(String.valueOf(value)).contains(normalizedNeedle)) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(Object value, String needle) {
        return needle == null || (value != null && normalize(String.valueOf(value)).contains(normalize(needle)));
    }

    public static boolean equalsText(Object value, String expected) {
        return expected == null || (value != null && normalize(String.valueOf(value)).equals(normalize(expected)));
    }

    public static boolean equalsBoolean(boolean value, Boolean expected) {
        return expected == null || value == expected;
    }

    private static int compareValues(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        if (left instanceof String leftString && right instanceof String rightString) {
            return leftString.compareToIgnoreCase(rightString);
        }
        if (left instanceof Comparable<?> comparable && left.getClass().isInstance(right)) {
            @SuppressWarnings("unchecked")
            Comparable<Object> typed = (Comparable<Object>) comparable;
            return typed.compareTo(right);
        }
        return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}