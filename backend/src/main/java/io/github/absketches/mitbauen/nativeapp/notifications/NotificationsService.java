package io.github.absketches.mitbauen.nativeapp.notifications;

import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.auth.SessionUser;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Optional;

import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

public class NotificationsService extends Service {

    private final DatabaseRuntime databaseRuntime;

    public NotificationsService(final DatabaseRuntime databaseRuntime) {
        this.databaseRuntime = databaseRuntime;
    }

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
        if (!NotificationsUtil.matches(event.payload())) {
            return;
        }
        if (event.payload().isMethodOptions()) {
            NotificationsUtil.respondOptions(event);
            return;
        }
        if (!event.payload().isMethodGet()) {
            NotificationsUtil.respondMethodNotAllowed(event);
            return;
        }
        handleNotificationsLookup(event);
    }

    private void handleNotificationsLookup(final Event<HttpObject, HttpObject> event) {
        final Optional<SessionUser> sessionUser = verifiedSessionUser(event);
        if (sessionUser.isEmpty()) {
            return;
        }
        NotificationsUtil.respondNotifications(
            event,
            NotificationsRepository.listNotifications(databaseRuntime.dataSource(), sessionUser.get().id())
        );
    }

    private Optional<SessionUser> verifiedSessionUser(final Event<HttpObject, HttpObject> event) {
        return AuthUtil.verifiedSessionUser(
            event,
            databaseRuntime.dataSource(),
            NotificationsUtil.AUTH_REQUIRED_CODE,
            NotificationsUtil.EMAIL_UNVERIFIED_CODE
        );
    }
}
