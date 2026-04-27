package io.github.absketches.mitbauen.nativeapp.db;

import com.zaxxer.hikari.HikariDataSource;

public class Database {

    private Database() {
    }

    public static HikariDataSource open(final String jdbcUrl, final String jdbcUser, final String jdbcPassword, final String poolName) {
        return DataSourceFactory.create(jdbcUrl, jdbcUser, jdbcPassword, poolName);
    }

    public static void close(final HikariDataSource dataSource) {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
