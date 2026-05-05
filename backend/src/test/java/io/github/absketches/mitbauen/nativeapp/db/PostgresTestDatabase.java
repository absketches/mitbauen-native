package io.github.absketches.mitbauen.nativeapp.db;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class PostgresTestDatabase {

    private static final PostgresTestContainer CONTAINER = new PostgresTestContainer();

    private PostgresTestDatabase() {
    }

    public static DatabaseConfig createDatabase(final String prefix) {
        return CONTAINER.createDatabase(prefix);
    }

    public record DatabaseConfig(String jdbcUrl, String jdbcUser, String jdbcPassword) {
    }

    private static final class PostgresTestContainer {

        private static final String IMAGE = "postgres:16-alpine";
        private static final String USER = "mitbauen_test";
        private static final String PASSWORD = "mitbauen_test_password";

        private final String containerId;
        private final String maintenanceJdbcUrl;

        private PostgresTestContainer() {
            containerId = lastNonEmptyLine(runCommand(
                "docker",
                "run",
                "--rm",
                "-d",
                "-e", "POSTGRES_USER=" + USER,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD,
                "-e", "POSTGRES_DB=postgres",
                "-P",
                IMAGE
            ));
            if (containerId.isEmpty()) {
                throw new IllegalStateException("Docker did not return a Postgres container id");
            }
            final String portOutput = runCommand("docker", "port", containerId, "5432/tcp").trim();
            if (portOutput.isEmpty()) {
                stopContainer();
                throw new IllegalStateException("Unable to determine published Postgres port for container " + containerId);
            }
            final String hostPort = portOutput.substring(portOutput.lastIndexOf(':') + 1).trim();
            maintenanceJdbcUrl = "jdbc:postgresql://127.0.0.1:" + hostPort + "/postgres";
            waitForReady();
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopContainer));
        }

        private DatabaseConfig createDatabase(final String prefix) {
            final String databaseName = normalizedDatabaseName(prefix);
            try (Connection connection = DriverManager.getConnection(maintenanceJdbcUrl, USER, PASSWORD);
                 Statement statement = connection.createStatement()) {
                statement.execute("create database " + databaseName);
            } catch (SQLException exception) {
                throw new IllegalStateException("Unable to create test database " + databaseName, exception);
            }
            return new DatabaseConfig(
                maintenanceJdbcUrl.substring(0, maintenanceJdbcUrl.lastIndexOf('/') + 1) + databaseName,
                USER,
                PASSWORD
            );
        }

        private void waitForReady() {
            final Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
            while (Instant.now().isBefore(deadline)) {
                try (Connection ignored = DriverManager.getConnection(maintenanceJdbcUrl, USER, PASSWORD)) {
                    return;
                } catch (SQLException ignored) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        stopContainer();
                        throw new IllegalStateException("Interrupted while waiting for Postgres test container", interruptedException);
                    }
                }
            }
            stopContainer();
            throw new IllegalStateException("Timed out waiting for Postgres test container to become ready");
        }

        private void stopContainer() {
            try {
                runCommand("docker", "rm", "-f", containerId);
            } catch (IllegalStateException ignored) {
                // Container may already be gone.
            }
        }

        private static String normalizedDatabaseName(final String prefix) {
            final String base = prefix
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
            return "mitbauen_" + base + "_" + UUID.randomUUID().toString().replace("-", "");
        }

        private static String runCommand(final String... command) {
            final Process process;
            try {
                process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to start command: " + String.join(" ", command), exception);
            }

            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                process.getInputStream().transferTo(output);
                final int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new IllegalStateException(
                        "Command failed (" + exitCode + "): "
                            + String.join(" ", command)
                            + System.lineSeparator()
                            + output.toString(StandardCharsets.UTF_8)
                    );
                }
                return output.toString(StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read command output: " + String.join(" ", command), exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for command: " + String.join(" ", command), exception);
            }
        }

        private static String lastNonEmptyLine(final String output) {
            final String[] lines = output.split("\\R");
            for (int index = lines.length - 1; index >= 0; index--) {
                final String line = lines[index].trim();
                if (!line.isEmpty()) {
                    return line;
                }
            }
            return "";
        }
    }
}
