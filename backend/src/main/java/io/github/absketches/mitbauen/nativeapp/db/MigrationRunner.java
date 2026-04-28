package io.github.absketches.mitbauen.nativeapp.db;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class MigrationRunner {

    public static final String MIGRATION_INDEX_RESOURCE = "db/migrations/index.txt";

    private final ClassLoader classLoader;

    public MigrationRunner() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public MigrationRunner(final ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void migrate(final DataSource dataSource) {
        final List<MigrationResource> migrations = migrationResources();
        try (Connection connection = dataSource.getConnection()) {
            ensureSchemaMigrationsTable(connection);
            for (MigrationResource migration : migrations) {
                if (isApplied(connection, migration.version())) {
                    continue;
                }
                applyMigration(connection, migration);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to apply database migrations", exception);
        }
    }

    private List<MigrationResource> migrationResources() {
        try (InputStream stream = openResource(MIGRATION_INDEX_RESOURCE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            final List<MigrationResource> migrations = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                final String filename = line.trim();
                if (filename.isEmpty() || filename.startsWith("#")) {
                    continue;
                }
                migrations.add(new MigrationResource(
                    versionFromFilename(filename),
                    filename,
                    readResource("db/migrations/" + filename)
                ));
            }
            return migrations;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load migration index from " + MIGRATION_INDEX_RESOURCE, exception);
        }
    }

    private void ensureSchemaMigrationsTable(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                create table if not exists schema_migrations (
                    version varchar(80) primary key,
                    filename varchar(255) not null,
                    applied_at timestamp not null default current_timestamp
                )
                """);
        }
    }

    private boolean isApplied(final Connection connection, final String version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "select 1 from schema_migrations where version = ?"
        )) {
            statement.setString(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void applyMigration(final Connection connection, final MigrationResource migration) throws SQLException {
        final boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement();
             PreparedStatement recordMigration = connection.prepareStatement(
                 "insert into schema_migrations (version, filename) values (?, ?)"
             )) {
            statement.execute(migration.sql());
            recordMigration.setString(1, migration.version());
            recordMigration.setString(2, migration.filename());
            recordMigration.executeUpdate();
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private String readResource(final String resourcePath) {
        try (InputStream stream = openResource(resourcePath)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read migration resource " + resourcePath, exception);
        }
    }

    private InputStream openResource(final String resourcePath) {
        final InputStream stream = classLoader.getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IllegalStateException("Missing resource on classpath: " + resourcePath);
        }
        return stream;
    }

    private static String versionFromFilename(final String filename) {
        final int prefixStart = filename.indexOf('V');
        final int delimiter = filename.indexOf("__");
        if (prefixStart != 0 || delimiter < 0) {
            throw new IllegalStateException("Invalid migration filename: " + filename);
        }
        return filename.substring(1, delimiter);
    }

    private record MigrationResource(String version, String filename, String sql) {
    }
}
