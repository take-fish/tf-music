package com.fishmatrix.music.util;

public final class Validation {

    private Validation() {
    }

    public static String text(String value, int maxLength, String field, boolean required) {
        String cleaned = value == null ? "" : value.strip();
        if (required && cleaned.isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        if (cleaned.codePointCount(0, cleaned.length()) > maxLength) {
            throw new IllegalArgumentException(field + "过长");
        }
        return cleaned;
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String cleaned = value.strip();
        if (cleaned.codePointCount(0, cleaned.length()) <= maxLength) {
            return cleaned;
        }
        int end = cleaned.offsetByCodePoints(0, maxLength);
        return cleaned.substring(0, end);
    }
}
