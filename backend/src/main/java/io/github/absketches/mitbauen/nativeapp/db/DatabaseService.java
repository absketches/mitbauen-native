package io.github.absketches.mitbauen.nativeapp.db;

import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;

import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;

public class DatabaseService extends Service {

    public static final String CONFIG_JDBC_DATABASE_URL = registerConfig("jdbc_database_url", "JDBC database URL");
    public static final String CONFIG_JDBC_DATABASE_USER = registerConfig("jdbc_database_user", "JDBC database user");
    public static final String CONFIG_JDBC_DATABASE_PASSWORD = registerConfig("jdbc_database_password", "JDBC database password");

    private final DatabaseRuntime databaseRuntime;

    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;

    public DatabaseService(final DatabaseRuntime databaseRuntime) {
        this.databaseRuntime = databaseRuntime;
    }

    @Override
    public void start() {
        try {
            databaseRuntime.start(jdbcUrl, jdbcUser, jdbcPassword);
            new MigrationRunner().migrate(databaseRuntime.openedDataSource());
            context.info(() -> "[{}] started", name());
        } catch (Throwable throwable) {
            databaseRuntime.fail(throwable);
            throw throwable;
        }
    }

    @Override
    public void stop() {
        databaseRuntime.stop();
    }

    @Override
    public Object onFailure(final Event<?, ?> error) {
        return error.payload();
    }

    @Override
    public void onEvent(final Event<?, ?> event) {
    }

    @Override
    public void configure(final TypeMapI<?> changes, final TypeMapI<?> merged) {
        jdbcUrl = merged.asStringOpt(CONFIG_JDBC_DATABASE_URL).orElse("");
        jdbcUser = merged.asStringOpt(CONFIG_JDBC_DATABASE_USER).orElse("");
        jdbcPassword = merged.asStringOpt(CONFIG_JDBC_DATABASE_PASSWORD).orElse("");
    }
}
