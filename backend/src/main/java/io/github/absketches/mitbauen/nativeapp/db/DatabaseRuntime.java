package io.github.absketches.mitbauen.nativeapp.db;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;

public class DatabaseRuntime {

    private final String poolName;
    private final CompletableFuture<HikariDataSource> startedDataSource = new CompletableFuture<>();
    private HikariDataSource dataSource;

    public DatabaseRuntime() {
        this("mitbauen");
    }

    public DatabaseRuntime(final String poolName) {
        this.poolName = poolName;
    }

    public synchronized void start(final String jdbcUrl, final String jdbcUser, final String jdbcPassword) {
        if (startedDataSource.isDone()) {
            return;
        }
        try {
            dataSource = Database.open(jdbcUrl, jdbcUser, jdbcPassword, poolName);
            startedDataSource.complete(dataSource);
        } catch (RuntimeException exception) {
            startedDataSource.completeExceptionally(exception);
            throw exception;
        }
    }

    public synchronized void stop() {
        Database.close(dataSource);
        dataSource = null;
    }

    public DataSource dataSource() {
        return startedDataSource.join();
    }
}
