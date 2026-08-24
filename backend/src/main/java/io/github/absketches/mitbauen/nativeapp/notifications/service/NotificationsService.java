package io.github.absketches.mitbauen.nativeapp.notifications.service;

import io.github.absketches.mitbauen.nativeapp.notifications.repository.NotificationsRepository;

import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.auth.model.SessionUser;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.http.ResponseUtil;
import io.github.absketches.mitbauen.nativeapp.notifications.NotificationsUtil;
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
            ResponseUtil.respondOptions(event);
            return;
        }
        if (!event.payload().isMethodGet()) {
            ResponseUtil.respondMethodNotAllowed(event, ResponseUtil.METHOD_NOT_ALLOWED_CODE);
            return;
        }
        handleNotificationsLookup(event);
    }

    private void handleNotificationsLookup(final Event<HttpObject, HttpObject> event) {
        final Optional<SessionUser> sessionUser = AuthUtil.verifiedSessionUser(
            event,
            databaseRuntime.dataSource(),
            NotificationsUtil.AUTH_REQUIRED_CODE,
            NotificationsUtil.EMAIL_UNVERIFIED_CODE
        );
        if (sessionUser.isEmpty()) {
            return;
        }
        ResponseUtil.respondOk(
            event,
            NotificationsUtil.notificationsPayload(NotificationsRepository.listNotifications(databaseRuntime.dataSource(), sessionUser.get().id()))
        );
    }

}
