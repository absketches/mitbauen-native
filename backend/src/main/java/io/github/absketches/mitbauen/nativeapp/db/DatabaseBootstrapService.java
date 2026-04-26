package io.github.absketches.mitbauen.nativeapp.db;

import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;

import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;

public class DatabaseBootstrapService extends Service {

    public static final String CONFIG_JDBC_DATABASE_URL = registerConfig(DatabaseConfig.CONFIG_JDBC_DATABASE_URL, "JDBC database URL");
    public static final String CONFIG_JDBC_DATABASE_USER = registerConfig(DatabaseConfig.CONFIG_JDBC_DATABASE_USER, "JDBC database user");
    public static final String CONFIG_JDBC_DATABASE_PASSWORD = registerConfig(DatabaseConfig.CONFIG_JDBC_DATABASE_PASSWORD, "JDBC database password");

    private final DatabaseRuntime databaseRuntime;
    private DatabaseConfig databaseConfig;

    public DatabaseBootstrapService(final DatabaseRuntime databaseRuntime) {
        this.databaseRuntime = databaseRuntime;
    }

    @Override
    public void start() {
        databaseRuntime.start(databaseConfig);
        context.info(() -> "[{}] started", name());
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
        databaseConfig = new DatabaseConfig(
            merged.asStringOpt(CONFIG_JDBC_DATABASE_URL).orElse(""),
            merged.asStringOpt(CONFIG_JDBC_DATABASE_USER).orElse(""),
            merged.asStringOpt(CONFIG_JDBC_DATABASE_PASSWORD).orElse("")
        );
    }
}
