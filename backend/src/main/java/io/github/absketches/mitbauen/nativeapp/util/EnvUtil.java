package io.github.absketches.mitbauen.nativeapp.util;

import java.util.Locale;

public final class EnvUtil {

    private EnvUtil() {
    }

    public static String requiredEnv(final String key) {
        final String directValue = System.getenv(key);
        if (directValue != null && !directValue.isBlank()) {
            return directValue;
        }

        final String upperCaseValue = System.getenv(key.toUpperCase(Locale.ROOT));
        if (upperCaseValue != null && !upperCaseValue.isBlank()) {
            return upperCaseValue;
        }

        throw new IllegalStateException("Missing required environment variable: " + key);
    }
}
