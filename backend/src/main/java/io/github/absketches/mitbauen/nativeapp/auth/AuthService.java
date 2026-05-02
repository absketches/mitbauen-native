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
        final AuthUtil.Route route = AuthUtil.match(event.payload());
        if (route == AuthUtil.Route.NO_MATCH) {
            return;
        }
        if (event.payload().isMethodOptions()) {
            AuthUtil.respondOptions(event);
            return;
        }
        handleHttpRequest(event, route);
    }

    protected void handleHttpRequest(final Event<HttpObject, HttpObject> event, final AuthUtil.Route route) {
        switch (event.payload().methodType()) {
            case GET -> handleGet(event, route);
            case POST -> handlePost(event, route);
            case PUT -> handlePut(event, route);
            default -> AuthUtil.respondMethodNotAllowed(event);
        }
    }

    protected void handleGet(final Event<HttpObject, HttpObject> event, final AuthUtil.Route route) {
        switch (route) {
            case INVITE_VALIDATE -> handleInviteValidate(event);
            case SESSION -> handleSessionLookup(event);
            case PROFILE -> handleProfileLookup(event);
            case PUBLIC_PROFILE -> handlePublicProfileLookup(event);
            default -> AuthUtil.respondMethodNotAllowed(event);
        }
    }

    protected void handlePost(final Event<HttpObject, HttpObject> event, final AuthUtil.Route route) {
        switch (route) {
            case REGISTER -> handleRegister(event);
            case LOGIN -> handleLogin(event);
            case LOGOUT -> handleLogout(event);
            default -> AuthUtil.respondMethodNotAllowed(event);
        }
    }

    protected void handlePut(final Event<HttpObject, HttpObject> event, final AuthUtil.Route route) {
        switch (route) {
            case PROFILE -> handleProfileUpdate(event);
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
                inviteLink -> AuthUtil.respondInviteValidation(event),
                () -> AuthUtil.respondInvalidInvite(event)
            );
    }

    protected void handleRegister(final Event<HttpObject, HttpObject> event) {
        final LinkedTypeMap body = AuthUtil.bodyAsMap(event.payload());
        final String inviteToken = body.asString("inviteToken");
        final String email = AuthUtil.normalizeEmail(body.asString("email"));
        final String displayName = safeTrim(body.asString("displayName"));
        final String bio = safeTrim(body.asString("bio"));
        final String password = body.asString("password");
        final boolean emailPublic = safeBoolean(body.get("emailPublic"));

        if (inviteToken == null || inviteToken.isBlank() || email.isBlank() || displayName.isBlank() || password == null || password.isBlank()) {
            AuthUtil.respondBadRequest(event, "Invite token, email, display name, and password are required.");
            return;
        }
        final Optional<String> registrationValidation = validateRegistrationProfileInput(displayName, bio, email);
        if (registrationValidation.isPresent()) {
            AuthUtil.respondBadRequest(event, registrationValidation.get());
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
        final String passwordHash = AuthUtil.hashPassword(password);
        final Optional<SessionUser> createdUser = AuthRepository.createUserFromInvite(
            databaseRuntime.dataSource(),
            invite.get(),
            email,
            displayName,
            passwordHash,
            bio,
            emailPublic
        );
        if (createdUser.isEmpty()) {
            AuthUtil.respondConflict(event, "An account already exists for that email.");
            return;
        }
        final SessionUser sessionUser = createdUser.get();
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

    protected void handleProfileLookup(final Event<HttpObject, HttpObject> event) {
        final Optional<SessionUser> sessionUser = AuthUtil.currentSessionUser(event.payload(), databaseRuntime.dataSource());
        if (sessionUser.isEmpty()) {
            AuthUtil.respondUnauthorized(event, "You must be signed in to view your profile.");
            return;
        }

        AuthRepository.findProfileByUserId(databaseRuntime.dataSource(), sessionUser.get().id())
            .ifPresentOrElse(
                profile -> AuthUtil.respondProfile(event, profile),
                () -> AuthUtil.respondNotFound(event, "Profile not found.")
            );
    }

    protected void handlePublicProfileLookup(final Event<HttpObject, HttpObject> event) {
        final Optional<String> publicId = AuthUtil.publicProfileId(event.payload());
        if (publicId.isEmpty()) {
            AuthUtil.respondNotFound(event, "Profile not found.");
            return;
        }

        AuthRepository.findPublicProfileByPublicId(databaseRuntime.dataSource(), publicId.get())
            .ifPresentOrElse(
                profile -> AuthUtil.respondPublicProfile(event, profile),
                () -> AuthUtil.respondNotFound(event, "Profile not found.")
            );
    }

    protected void handleProfileUpdate(final Event<HttpObject, HttpObject> event) {
        final Optional<SessionUser> sessionUser = AuthUtil.currentSessionUser(event.payload(), databaseRuntime.dataSource());
        if (sessionUser.isEmpty()) {
            AuthUtil.respondUnauthorized(event, "You must be signed in to update your profile.");
            return;
        }

        final LinkedTypeMap body = AuthUtil.bodyAsMap(event.payload());
        final String displayName = safeTrim(body.asString("displayName"));
        final String bio = safeTrim(body.asString("bio"));
        final boolean emailPublic = safeBoolean(body.get("emailPublic"));

        final Optional<String> validation = validateEditableProfileInput(displayName, bio);
        if (validation.isPresent()) {
            AuthUtil.respondBadRequest(event, validation.get());
            return;
        }

        final UserProfile profile = AuthRepository.updateProfile(
            databaseRuntime.dataSource(),
            sessionUser.get().id(),
            displayName,
            bio,
            emailPublic
        );
        AuthUtil.respondProfile(event, profile);
    }

    protected void handleLogout(final Event<HttpObject, HttpObject> event) {
        AuthUtil.readSessionToken(event.payload())
            .ifPresent(token -> AuthRepository.deleteSession(databaseRuntime.dataSource(), AuthUtil.hashToken(token)));
        AuthUtil.respondLogout(event, AuthUtil.clearedSessionCookie(event.payload()));
    }

    private static String safeTrim(final String value) {
        return value == null ? "" : value.trim();
    }

    private static Optional<String> validateRegistrationProfileInput(final String displayName, final String bio, final String email) {
        final Optional<String> editableValidation = validateEditableProfileInput(displayName, bio);
        if (editableValidation.isPresent()) {
            return editableValidation;
        }
        if (email.isBlank()) {
            return Optional.of("Email is required.");
        }
        if (email.length() > AuthUtil.EMAIL_MAX_LENGTH) {
            return Optional.of("Email must be 320 characters or fewer.");
        }
        if (!email.contains("@")) {
            return Optional.of("Email must be a valid address.");
        }
        return Optional.empty();
    }

    private static Optional<String> validateEditableProfileInput(final String displayName, final String bio) {
        if (displayName.length() < AuthUtil.DISPLAY_NAME_MIN_LENGTH || displayName.length() > AuthUtil.DISPLAY_NAME_MAX_LENGTH) {
            return Optional.of("Display name must be between 2 and 120 characters.");
        }
        if (bio.length() > AuthUtil.BIO_MAX_LENGTH) {
            return Optional.of("Bio must be 560 characters or fewer.");
        }
        return Optional.empty();
    }

    private static boolean safeBoolean(final Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }
}
