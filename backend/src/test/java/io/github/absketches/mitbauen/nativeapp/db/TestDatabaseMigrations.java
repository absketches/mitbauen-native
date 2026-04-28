package io.github.absketches.mitbauen.nativeapp.db;

import com.zaxxer.hikari.HikariDataSource;
import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class TestDatabaseMigrations {

    private TestDatabaseMigrations() {
    }

    public static void migrate(final String jdbcUrl, final String jdbcUser, final String jdbcPassword) {
        try (HikariDataSource dataSource = Database.open(jdbcUrl, jdbcUser, jdbcPassword, "mitbauen-test-migrations")) {
            new MigrationRunner(TestDatabaseMigrations.class.getClassLoader()).migrate(dataSource);
        }
    }

    public static void seedInvite(
        final String jdbcUrl,
        final String jdbcUser,
        final String jdbcPassword,
        final String token
    ) {
        try (HikariDataSource dataSource = Database.open(jdbcUrl, jdbcUser, jdbcPassword, "mitbauen-test-seed");
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 insert into invite_links (token_hash, is_active, use_count)
                 values (?, true, 0)
                 """)) {
            statement.setString(1, AuthUtil.hashToken(token));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to seed test invite", exception);
        }
    }
}
