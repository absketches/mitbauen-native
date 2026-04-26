package io.github.absketches.mitbauen.nativeapp.db;

import com.zaxxer.hikari.HikariDataSource;

public class Database {

    private Database() {
    }

    public static HikariDataSource open(final DatabaseConfig config, final String poolName) {
        final HikariDataSource dataSource = DataSourceFactory.create(config, poolName);
        if (config.runMigrations()) {
            DatabaseMigrator.migrate(config);
        }
        return dataSource;
    }

    public static void close(final HikariDataSource dataSource) {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
