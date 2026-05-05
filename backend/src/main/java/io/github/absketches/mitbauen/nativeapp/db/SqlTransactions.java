package io.github.absketches.mitbauen.nativeapp.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class SqlTransactions {

    private SqlTransactions() {
    }

    public static <T> T execute(
        final DataSource dataSource,
        final String failureMessage,
        final Work<T> work
    ) {
        return execute(dataSource, failureMessage, work, (connection, exception) -> {
            throw exception;
        });
    }

    public static <T> T execute(
        final DataSource dataSource,
        final String failureMessage,
        final Work<T> work,
        final SqlFailureHandler<T> sqlFailureHandler
    ) {
        try (Connection connection = dataSource.getConnection()) {
            final boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                final T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException exception) {
                connection.rollback();
                return sqlFailureHandler.handle(connection, exception);
            } catch (RuntimeException | Error throwable) {
                connection.rollback();
                throw throwable;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(failureMessage, exception);
        }
    }

    public static <T> T execute(final Connection connection, final Work<T> work) throws SQLException {
        final boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            final T result = work.run(connection);
            connection.commit();
            return result;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } catch (RuntimeException | Error throwable) {
            connection.rollback();
            throw throwable;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlFailureHandler<T> {
        T handle(Connection connection, SQLException exception) throws SQLException;
    }
}
