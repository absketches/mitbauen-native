package io.github.absketches.mitbauen.nativeapp.db;

public record DatabaseConfig(
    String jdbcUrl,
    String jdbcUser,
    String jdbcPassword
) {

    public static final String CONFIG_JDBC_DATABASE_URL = "jdbc_database_url";
    public static final String CONFIG_JDBC_DATABASE_USER = "jdbc_database_user";
    public static final String CONFIG_JDBC_DATABASE_PASSWORD = "jdbc_database_password";
    public static final String ENV_JDBC_DATABASE_URL = "JDBC_DATABASE_URL";
    public static final String ENV_JDBC_DATABASE_USER = "JDBC_DATABASE_USER";
    public static final String ENV_JDBC_DATABASE_PASSWORD = "JDBC_DATABASE_PASSWORD";
}
