package io.github.absketches.mitbauen.nativeapp.db;

public record DatabaseConfig(
    String jdbcUrl,
    String jdbcUser,
    String jdbcPassword,
    boolean runMigrations,
    String migrationLocations
) {

    public static final String CONFIG_APP_RUN_MIGRATIONS = "app_run_migrations";
    public static final String CONFIG_APP_MIGRATION_LOCATIONS = "app_migration_locations";
    public static final String CONFIG_JDBC_DATABASE_URL = "jdbc_database_url";
    public static final String CONFIG_JDBC_DATABASE_USER = "jdbc_database_user";
    public static final String CONFIG_JDBC_DATABASE_PASSWORD = "jdbc_database_password";
    public static final String ENV_APP_RUN_MIGRATIONS = "APP_RUN_MIGRATIONS";
    public static final String ENV_APP_MIGRATION_LOCATIONS = "APP_MIGRATION_LOCATIONS";
    public static final String ENV_JDBC_DATABASE_URL = "JDBC_DATABASE_URL";
    public static final String ENV_JDBC_DATABASE_USER = "JDBC_DATABASE_USER";
    public static final String ENV_JDBC_DATABASE_PASSWORD = "JDBC_DATABASE_PASSWORD";
    public static final String DEFAULT_MIGRATION_LOCATIONS = "filesystem:../db/migrations";
}
