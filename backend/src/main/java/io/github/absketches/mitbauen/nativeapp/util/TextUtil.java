package io.github.absketches.mitbauen.nativeapp.util;

public final class TextUtil {

    private TextUtil() {
    }

    public static String trimToEmpty(final String value) {
        return value == null ? "" : value.trim();
    }

    public static String trimToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
