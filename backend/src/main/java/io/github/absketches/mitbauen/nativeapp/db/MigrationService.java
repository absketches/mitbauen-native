package io.github.absketches.mitbauen.nativeapp.db;

import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;

public class MigrationService extends Service {

    private final DatabaseRuntime databaseRuntime;
    private volatile Throwable failure;

    public MigrationService(final DatabaseRuntime databaseRuntime) {
        this.databaseRuntime = databaseRuntime;
    }

    @Override
    public void start() {
        try {
            new MigrationRunner().migrate(databaseRuntime.dataSource());
            context.info(() -> "[{}] completed", name());
        } catch (Throwable throwable) {
            failure = throwable;
            context.error(throwable, () -> "[{}] failed", name());
        } finally {
            context.nano().stop(MigrationService.class);
        }
    }

    @Override
    public void stop() {
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
    }

    public Throwable failure() {
        return failure;
    }
}
