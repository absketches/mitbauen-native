package io.github.absketches.mitbauen.nativeapp.db;

import java.util.Locale;

public record DatabaseConfig(String jdbcUrl, String jdbcUser, String jdbcPassword) {

    public static final String ENV_JDBC_DATABASE_URL = "jdbc_database_url";
    public static final String ENV_JDBC_DATABASE_USER = "jdbc_database_user";
    public static final String ENV_JDBC_DATABASE_PASSWORD = "jdbc_database_password";

    public static DatabaseConfig fromEnvironment() {
        return new DatabaseConfig(
            requiredEnv(ENV_JDBC_DATABASE_URL),
            requiredEnv(ENV_JDBC_DATABASE_USER),
            requiredEnv(ENV_JDBC_DATABASE_PASSWORD)
        );
    }

    private static String requiredEnv(final String key) {
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
