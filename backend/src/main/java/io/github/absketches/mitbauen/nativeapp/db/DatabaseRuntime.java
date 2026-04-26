package io.github.absketches.mitbauen.nativeapp.db;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public class DatabaseRuntime {

    private final String poolName;
    private HikariDataSource dataSource;

    public DatabaseRuntime() {
        this("mitbauen");
    }

    public DatabaseRuntime(final String poolName) {
        this.poolName = poolName;
    }

    public void start(final DatabaseConfig databaseConfig) {
        dataSource = Database.open(databaseConfig, poolName);
    }

    public void stop() {
        Database.close(dataSource);
        dataSource = null;
    }

    public DataSource dataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("Database datasource is not initialized yet.");
        }
        return dataSource;
    }
}
