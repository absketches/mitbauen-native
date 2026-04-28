package io.github.absketches.mitbauen.nativeapp.auth;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.time.Instant;
import java.util.Optional;

import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

public class AuthService extends Service {

    private final DatabaseRuntime databaseRuntime;

    public AuthService(final DatabaseRuntime databaseRuntime) {
        this.databaseRuntime = databaseRuntime;
    }

    @Override
    public void start() {
        context.info(() -> "[{}] started", name());
    }

    @Override
    public void stop() {
    }

    @Override
    public Object onFailure(final Event<?, ?> error) {
        error.channel(EVENT_HTTP_REQUEST).ifPresent(httpEvent -> {
            context.error(error.error(), () -> "[{}] request failed for {}", name(), httpEvent.payload().path());
            handleHttpFailure(httpEvent);
        });
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
        final AuthUtil.RoutesMatch route = AuthUtil.match(event.payload());
        if (route instanceof AuthUtil.NoMatch) {
            return;
        }
        if (event.payload().isMethodOptions()) {
            AuthUtil.respondOptions(event);
            return;
        }
        handleHttpRequest(event, route);
    }

    protected void handleHttpRequest(final Event<HttpObject, HttpObject> event, final AuthUtil.RoutesMatch route) {
        switch (event.payload().methodType()) {
            case GET -> handleGet(event, route);
            case POST -> handlePost(event, route);
            default -> AuthUtil.respondMethodNotAllowed(event);
        }
    }

    protected void handleGet(final Event<HttpObject, HttpObject> event, final AuthUtil.RoutesMatch route) {
        switch (route) {
            case AuthUtil.InviteValidateRoute __ -> handleInviteValidate(event);
            case AuthUtil.SessionRoute __ -> handleSessionLookup(event);
            default -> AuthUtil.respondMethodNotAllowed(event);
        }
    }

    protected void handlePost(final Event<HttpObject, HttpObject> event, final AuthUtil.RoutesMatch route) {
        switch (route) {
            case AuthUtil.RegisterRoute __ -> handleRegister(event);
            case AuthUtil.LoginRoute __ -> handleLogin(event);
            case AuthUtil.LogoutRoute __ -> handleLogout(event);
            default -> AuthUtil.respondMethodNotAllowed(event);
        }
    }

    protected void handleInviteValidate(final Event<HttpObject, HttpObject> event) {
        final String token = event.payload().queryParam("token");
        if (token == null || token.isBlank()) {
            AuthUtil.respondInvalidInvite(event);
            return;
        }
        AuthRepository.findInviteByToken(databaseRuntime.dataSource(), token)
            .filter(InviteLink::active)
            .ifPresentOrElse(
                inviteLink -> AuthUtil.respondInviteValidation(event, inviteLink),
                () -> AuthUtil.respondInvalidInvite(event)
            );
    }

    protected void handleRegister(final Event<HttpObject, HttpObject> event) {
        final LinkedTypeMap body = AuthUtil.bodyAsMap(event.payload());
        final String inviteToken = body.asString("inviteToken");
        final String email = AuthUtil.normalizeEmail(body.asString("email"));
        final String displayName = safeTrim(body.asString("displayName"));
        final String password = body.asString("password");

        if (inviteToken == null || inviteToken.isBlank() || email.isBlank() || displayName.isBlank() || password == null || password.isBlank()) {
            AuthUtil.respondBadRequest(event, "Invite token, email, display name, and password are required.");
            return;
        }
        if (!AuthUtil.meetsPasswordRequirements(password)) {
            AuthUtil.respondBadRequest(event, AuthUtil.PASSWORD_REQUIREMENTS_MESSAGE);
            return;
        }

        final Optional<InviteLink> invite = AuthRepository.findInviteByToken(databaseRuntime.dataSource(), inviteToken).filter(InviteLink::active);
        if (invite.isEmpty()) {
            AuthUtil.respondBadRequest(event, "Invite link is invalid.");
            return;
        }
        if (invite.get().allowedEmail() != null && !invite.get().allowedEmail().equalsIgnoreCase(email)) {
            AuthUtil.respondForbidden(event, "This invite link is restricted to a different email address.");
            return;
        }
        if (AuthRepository.emailExists(databaseRuntime.dataSource(), email)) {
            AuthUtil.respondConflict(event, "An account already exists for that email.");
            return;
        }

        final String passwordHash = AuthUtil.hashPassword(password);
        final SessionUser sessionUser = AuthRepository.createUserFromInvite(
            databaseRuntime.dataSource(),
            invite.get(),
            inviteToken,
            email,
            displayName,
            passwordHash
        );
        final String sessionToken = AuthUtil.newSessionToken();
        AuthRepository.createSession(
            databaseRuntime.dataSource(),
            sessionUser.id(),
            AuthUtil.hashToken(sessionToken),
            Instant.now().plus(AuthUtil.SESSION_TTL)
        );
        AuthUtil.respondAuthSuccess(event, sessionUser, AuthUtil.sessionCookie(event.payload(), sessionToken), 201);
    }

    protected void handleLogin(final Event<HttpObject, HttpObject> event) {
        final LinkedTypeMap body = AuthUtil.bodyAsMap(event.payload());
        final String email = AuthUtil.normalizeEmail(body.asString("email"));
        final String password = body.asString("password");
        if (email.isBlank() || password == null || password.isBlank()) {
            AuthUtil.respondBadRequest(event, "Email and password are required.");
            return;
        }

        final Optional<LoginIdentity> loginIdentity = AuthRepository.findLoginIdentityByEmail(databaseRuntime.dataSource(), email);
        if (loginIdentity.isEmpty() || !AuthUtil.verifyPassword(password, loginIdentity.get().passwordHash())) {
            AuthUtil.respondUnauthorized(event, "Email or password is incorrect.");
            return;
        }

        final String sessionToken = AuthUtil.newSessionToken();
        AuthRepository.createSession(
            databaseRuntime.dataSource(),
            loginIdentity.get().userId(),
            AuthUtil.hashToken(sessionToken),
            Instant.now().plus(AuthUtil.SESSION_TTL)
        );
        AuthUtil.respondAuthSuccess(event, loginIdentity.get().toSessionUser(), AuthUtil.sessionCookie(event.payload(), sessionToken), 200);
    }

    protected void handleSessionLookup(final Event<HttpObject, HttpObject> event) {
        final Optional<String> sessionToken = AuthUtil.readSessionToken(event.payload());
        if (sessionToken.isEmpty()) {
            AuthUtil.respondAnonymousSession(event);
            return;
        }
        AuthRepository.findSessionUserByTokenHash(databaseRuntime.dataSource(), AuthUtil.hashToken(sessionToken.get()))
            .ifPresentOrElse(
                sessionUser -> AuthUtil.respondSession(event, sessionUser),
                () -> AuthUtil.respondAnonymousSession(event)
            );
    }

    protected void handleLogout(final Event<HttpObject, HttpObject> event) {
        AuthUtil.readSessionToken(event.payload())
            .ifPresent(token -> AuthRepository.deleteSession(databaseRuntime.dataSource(), AuthUtil.hashToken(token)));
        AuthUtil.respondLogout(event, AuthUtil.clearedSessionCookie(event.payload()));
    }

    protected void handleHttpFailure(final Event<HttpObject, HttpObject> event) {
        switch (AuthUtil.match(event.payload())) {
            case AuthUtil.NoMatch __ -> {
            }
            default -> AuthUtil.respondFailure(event, event.error());
        }
    }

    private static String safeTrim(final String value) {
        return value == null ? "" : value.trim();
    }
}
