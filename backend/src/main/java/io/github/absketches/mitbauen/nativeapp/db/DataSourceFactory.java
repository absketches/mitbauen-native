package io.github.absketches.mitbauen.nativeapp.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DataSourceFactory {

    private DataSourceFactory() {
    }

    public static HikariDataSource create(final DatabaseConfig config, final String poolName) {
        if (config.jdbcUrl() == null || config.jdbcUrl().isBlank()) {
            throw new IllegalStateException(
                "Missing JDBC database URL. Set " + DatabaseConfig.ENV_JDBC_DATABASE_URL + " or configure "
                    + DatabaseConfig.CONFIG_JDBC_DATABASE_URL + " through Nano config."
            );
        }
        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.jdbcUser());
        hikariConfig.setPassword(config.jdbcPassword());
        hikariConfig.setMaximumPoolSize(4);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setAutoCommit(true);
        hikariConfig.setPoolName(poolName);
        return new HikariDataSource(hikariConfig);
    }
}
