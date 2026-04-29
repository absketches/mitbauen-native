package io.github.absketches.mitbauen.nativeapp.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DataSourceFactory {

    private DataSourceFactory() {
    }

    public static HikariDataSource create(
        final String jdbcUrl,
        final String jdbcUser,
        final String jdbcPassword,
        final String poolName
    ) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalStateException(
                "Missing JDBC database URL. Configure "
                    + DatabaseConfig.ENV_JDBC_DATABASE_URL + " in the environment."
            );
        }
        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(jdbcUser);
        hikariConfig.setPassword(jdbcPassword);
        hikariConfig.setMaximumPoolSize(4);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setAutoCommit(true);
        hikariConfig.setPoolName(poolName);
        return new HikariDataSource(hikariConfig);
    }
}
