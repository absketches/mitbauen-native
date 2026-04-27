package io.github.absketches.mitbauen.nativeapp.db;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;

public class DatabaseRuntime {

    private final String poolName;
    private final CompletableFuture<HikariDataSource> readyDataSource = new CompletableFuture<>();
    private HikariDataSource dataSource;

    public DatabaseRuntime() {
        this("mitbauen");
    }

    public DatabaseRuntime(final String poolName) {
        this.poolName = poolName;
    }

    public synchronized void start(final String jdbcUrl, final String jdbcUser, final String jdbcPassword) {
        if (dataSource != null) {
            return;
        }
        dataSource = Database.open(jdbcUrl, jdbcUser, jdbcPassword, poolName);
    }

    public synchronized DataSource openedDataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("Database runtime has not been started yet.");
        }
        return dataSource;
    }

    public synchronized void markReady() {
        if (dataSource != null && !readyDataSource.isDone()) {
            readyDataSource.complete(dataSource);
        }
    }

    public synchronized void fail(final Throwable throwable) {
        if (!readyDataSource.isDone()) {
            readyDataSource.completeExceptionally(throwable);
        }
    }

    public synchronized void stop() {
        Database.close(dataSource);
        dataSource = null;
    }

    public DataSource dataSource() {
        return readyDataSource.join();
    }
}
