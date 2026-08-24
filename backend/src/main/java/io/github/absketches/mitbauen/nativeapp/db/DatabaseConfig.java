package io.github.absketches.mitbauen.nativeapp.db;

import io.github.absketches.mitbauen.nativeapp.util.EnvUtil;

public record DatabaseConfig(String jdbcUrl, String jdbcUser, String jdbcPassword) {

    public static final String ENV_JDBC_DATABASE_URL = "jdbc_database_url";
    public static final String ENV_JDBC_DATABASE_USER = "jdbc_database_user";
    public static final String ENV_JDBC_DATABASE_PASSWORD = "jdbc_database_password";

    public static DatabaseConfig fromEnvironment() {
        return new DatabaseConfig(
            EnvUtil.requiredEnv(ENV_JDBC_DATABASE_URL),
            EnvUtil.requiredEnv(ENV_JDBC_DATABASE_USER),
            EnvUtil.requiredEnv(ENV_JDBC_DATABASE_PASSWORD)
        );
    }
}
