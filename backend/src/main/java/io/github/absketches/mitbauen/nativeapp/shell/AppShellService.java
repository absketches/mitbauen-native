package io.github.absketches.mitbauen.nativeapp.shell;

import berlin.yuna.typemap.model.TypeMapI;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

public class AppShellService extends Service {

    @Override
    public void start() {
        context.info(() -> "[{}] started", name());
    }

    @Override
    public void stop() {
        context.info(() -> "[{}] stopped", name());
    }

    @Override
    public Object onFailure(final Event<?, ?> error) {
        return error.payload();
    }

    @Override
    public void onEvent(final Event<?, ?> event) {
        event.channel(EVENT_HTTP_REQUEST).ifPresent(this::handleHttpEvent);
    }

    @Override
    public void configure(final TypeMapI<?> changes, final TypeMapI<?> merged) {
    }

    protected void handleHttpEvent(final Event<HttpObject, HttpObject> event) {
        final AppShellUtil.RoutesMatch route = AppShellUtil.match(event.payload());
        if (route instanceof AppShellUtil.NoMatch) {
            return;
        }
        if (event.payload().isMethodOptions()) {
            AppShellUtil.respondOptions(event);
            return;
        }
        handleHttpRequest(event, route);
    }

    protected void handleHttpRequest(final Event<HttpObject, HttpObject> event, final AppShellUtil.RoutesMatch route) {
        switch (event.payload().methodType()) {
            case GET, HEAD -> handleGet(event, route);
            default -> AppShellUtil.respondMethodNotAllowed(event);
        }
    }

    protected void handleGet(final Event<HttpObject, HttpObject> event, final AppShellUtil.RoutesMatch route) {
        switch (route) {
            case AppShellUtil.AssetRoute assetRoute -> AppShellUtil.respondAsset(event, assetRoute);
            case AppShellUtil.NoMatch __ -> {
            }
        }
    }

    protected void handleHttpFailure(final Event<HttpObject, HttpObject> event, final Throwable error) {
        switch (AppShellUtil.match(event.payload())) {
            case AppShellUtil.AssetRoute __ -> AppShellUtil.respondFailure(event, error);
            case AppShellUtil.NoMatch __ -> {
            }
        }
    }
}
