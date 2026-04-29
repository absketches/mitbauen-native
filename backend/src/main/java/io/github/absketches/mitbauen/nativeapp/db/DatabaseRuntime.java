package io.github.absketches.mitbauen.nativeapp.db;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public class DatabaseRuntime {

    private final HikariDataSource dataSource;

    public DatabaseRuntime(final String jdbcUrl, final String jdbcUser, final String jdbcPassword) {
        this(jdbcUrl, jdbcUser, jdbcPassword, "mitbauen");
    }

    public DatabaseRuntime(final String jdbcUrl, final String jdbcUser, final String jdbcPassword, final String poolName) {
        this.dataSource = Database.open(jdbcUrl, jdbcUser, jdbcPassword, poolName);
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public void stop() {
        Database.close(dataSource);
    }
}
