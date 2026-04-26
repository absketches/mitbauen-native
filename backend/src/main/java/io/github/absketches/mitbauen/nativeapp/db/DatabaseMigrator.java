package io.github.absketches.mitbauen.nativeapp.db;

import org.flywaydb.core.Flyway;

import java.util.Arrays;

public class DatabaseMigrator {

    private DatabaseMigrator() {
    }

    public static void migrate(final DatabaseConfig config) {
        Flyway.configure()
            .dataSource(config.jdbcUrl(), config.jdbcUser(), config.jdbcPassword())
            .locations(Arrays.stream(config.migrationLocations().split(","))
                .map(String::trim)
                .filter(location -> !location.isBlank())
                .toArray(String[]::new))
            .load()
            .migrate();
    }
}
