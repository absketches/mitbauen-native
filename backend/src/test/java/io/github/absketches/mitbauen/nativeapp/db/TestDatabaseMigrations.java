package io.github.absketches.mitbauen.nativeapp.db;

import org.h2.tools.RunScript;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;

public final class TestDatabaseMigrations {

    private TestDatabaseMigrations() {
    }

    public static void migrate(final String jdbcUrl, final String jdbcUser, final String jdbcPassword) {
        final File migrationsDirectory = new File("../db/migrations");
        final File[] migrationFiles = migrationsDirectory.listFiles((dir, name) -> name.matches("V.+__.+\\.sql"));
        if (migrationFiles == null) {
            throw new IllegalStateException("Unable to list test migrations from " + migrationsDirectory.getAbsolutePath());
        }

        Arrays.sort(migrationFiles, TestDatabaseMigrations::compareByVersion);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            for (File migrationFile : migrationFiles) {
                try (FileReader reader = new FileReader(migrationFile)) {
                    RunScript.execute(connection, reader);
                }
            }
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Unable to apply test migrations", exception);
        }
    }

    private static String versionKey(final File file) {
        return file.getName()
            .replaceFirst("^V", "")
            .replaceFirst("__.*$", "");
    }

    private static int compareByVersion(final File left, final File right) {
        final String[] leftParts = versionKey(left).split("[^0-9]+");
        final String[] rightParts = versionKey(right).split("[^0-9]+");
        final int maxLength = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < maxLength; index++) {
            final int leftValue = index < leftParts.length && !leftParts[index].isBlank() ? Integer.parseInt(leftParts[index]) : 0;
            final int rightValue = index < rightParts.length && !rightParts[index].isBlank() ? Integer.parseInt(rightParts[index]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return left.getName().compareTo(right.getName());
    }
}
