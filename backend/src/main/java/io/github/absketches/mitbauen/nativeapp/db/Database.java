package io.github.absketches.mitbauen.nativeapp.db;

import com.zaxxer.hikari.HikariDataSource;

public class Database {

    private Database() {
    }

    public static HikariDataSource open(final DatabaseConfig config, final String poolName) {
        return DataSourceFactory.create(config, poolName);
    }

    public static void close(final HikariDataSource dataSource) {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
