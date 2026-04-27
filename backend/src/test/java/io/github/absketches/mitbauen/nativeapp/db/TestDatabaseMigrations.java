package io.github.absketches.mitbauen.nativeapp.db;

import com.zaxxer.hikari.HikariDataSource;

public final class TestDatabaseMigrations {

    private TestDatabaseMigrations() {
    }

    public static void migrate(final String jdbcUrl, final String jdbcUser, final String jdbcPassword) {
        try (HikariDataSource dataSource = Database.open(jdbcUrl, jdbcUser, jdbcPassword, "mitbauen-test-migrations")) {
            new MigrationRunner(TestDatabaseMigrations.class.getClassLoader()).migrate(dataSource);
        }
    }
}
