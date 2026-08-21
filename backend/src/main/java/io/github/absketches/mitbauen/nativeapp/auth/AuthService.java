package io.github.absketches.mitbauen.nativeapp.auth;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.http.ResponseUtil;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

public class AuthService extends Service {

    private static final int MAX_VERIFICATION_EMAILS_PER_24_HOURS = 1;

    private final DatabaseRuntime databaseRuntime;
    private final EmailVerificationSettings emailVerificationSettings;
    private final TransactionalEmailSender transactionalEmailSender;

    public AuthService(
        final DatabaseRuntime databaseRuntime,
        final EmailVerificationSettings emailVerificationSettings,
        final TransactionalEmailSender transactionalEmailSender
    ) {
        this.databaseRuntime = databaseRuntime;
        this.emailVerificationSettings = emailVerificationSettings;
        this.transactionalEmailSender = transactionalEmailSender;
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
            ResponseUtil.respondOptions(event);
            return;
        }
        handleHttpRequest(event, route);
    }

    protected void handleHttpRequest(final Event<HttpObject, HttpObject> event, final AuthUtil.Route route) {
        switch (event.payload().methodType()) {
            case GET -> handleGet(event, route);
            case POST -> handlePost(event, route);
            case PUT -> handlePut(event, route);
            case DELETE -> handleDelete(event, route);
            default -> ResponseUtil.respondMethodNotAllowed(event, AuthUtil.METHOD_NOT_ALLOWED_CODE);
        }
    }

    protected void handleGet(final Event<HttpObject, HttpObject> event, final AuthUtil.Route route) {
        switch (route) {
            case INVITE_VALIDATE -> handleInviteValidate(event);
            case SESSION -> handleSessionLookup(event);
            case PROFILE -> handleProfileLookup(event);
            case PUBLIC_PROFILE -> handlePublicProfileLookup(event);
            default -> ResponseUtil.respondMethodNotAllowed(event, AuthUtil.METHOD_NOT_ALLOWED_CODE);
        }
    }

    protected void handlePost(final Event<HttpObject, HttpObject> event, final AuthUtil.Route route) {
        switch (route) {
            case REGISTER -> handleRegister(event);
            case LOGIN -> handleLogin(event);
            case LOGOUT -> handleLogout(event);
            case VERIFY_EMAIL_REQUEST -> handleVerifyEmailRequest(event);
            case VERIFY_EMAIL_CONFIRM -> handleVerifyEmailConfirm(event);
            case PASSWORD_RESET_REQUEST -> handlePasswordResetRequest(event);
            case PASSWORD_RESET_CONFIRM -> handlePasswordResetConfirm(event);
            default -> ResponseUtil.respondMethodNotAllowed(event, AuthUtil.METHOD_NOT_ALLOWED_CODE);
        }
    }

    protected void handlePut(final Event<HttpObject, HttpObject> event, final AuthUtil.Route route) {
        switch (route) {
            case PROFILE -> handleProfileUpdate(event);
            default -> ResponseUtil.respondMethodNotAllowed(event, AuthUtil.METHOD_NOT_ALLOWED_CODE);
        }
    }

    protected void handleDelete(final Event<HttpObject, HttpObject> event, final AuthUtil.Route route) {
        switch (route) {
            case PROFILE -> handleAccountDelete(event);
            default -> ResponseUtil.respondMethodNotAllowed(event, AuthUtil.METHOD_NOT_ALLOWED_CODE);
        }
    }

    protected void handleInviteValidate(final Event<HttpObject, HttpObject> event) {
        final String token = event.payload().queryParam("token");
        if (token == null || token.isBlank()) {
            ResponseUtil.respondOk(event, Map.of("valid", false));
            return;
        }
        AuthRepository.findInviteByToken(databaseRuntime.dataSource(), token)
            .filter(InviteLink::active)
            .ifPresentOrElse(
                inviteLink -> ResponseUtil.respondOk(event, Map.of("valid", true)),
                () -> ResponseUtil.respondOk(event, Map.of("valid", false))
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
            ResponseUtil.respondBadRequest(event, AuthUtil.REGISTRATION_REQUIRED_CODE);
            return;
        }
        final Optional<String> registrationValidation = validateRegistrationProfileInput(displayName, bio, email);
        if (registrationValidation.isPresent()) {
            ResponseUtil.respondBadRequest(event, registrationValidation.get());
            return;
        }
        if (!AuthUtil.meetsPasswordRequirements(password)) {
            ResponseUtil.respondBadRequest(event, AuthUtil.PASSWORD_REQUIREMENTS_CODE);
            return;
        }

        final Optional<InviteLink> invite = AuthRepository.findInviteByToken(databaseRuntime.dataSource(), inviteToken).filter(InviteLink::active);
        if (invite.isEmpty()) {
            ResponseUtil.respondBadRequest(event, AuthUtil.INVITE_INVALID_CODE);
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
            emailPublic,
            false
        );
        if (createdUser.isEmpty()) {
            ResponseUtil.respondConflict(event, AuthUtil.EMAIL_EXISTS_CODE);
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
        if (!sessionUser.emailVerified()) {
            try {
                issueInitialVerificationEmail(sessionUser);
            } catch (RuntimeException exception) {
                context.warn(() -> "Unable to send initial verification email for {}", sessionUser.email());
            }
        }
        AuthUtil.respondAuthSuccess(event, sessionUser, AuthUtil.sessionCookie(event.payload(), sessionToken), 201);
    }

    protected void handleLogin(final Event<HttpObject, HttpObject> event) {
        final LinkedTypeMap body = AuthUtil.bodyAsMap(event.payload());
        final String email = AuthUtil.normalizeEmail(body.asString("email"));
        final String password = body.asString("password");
        if (email.isBlank() || password == null || password.isBlank()) {
            ResponseUtil.respondBadRequest(event, AuthUtil.LOGIN_REQUIRED_CODE);
            return;
        }

        final Optional<LoginIdentity> loginIdentity = AuthRepository.findLoginIdentityByEmail(databaseRuntime.dataSource(), email);
        if (loginIdentity.isEmpty() || !AuthUtil.verifyPassword(password, loginIdentity.get().passwordHash())) {
            ResponseUtil.respondUnauthorized(event, AuthUtil.LOGIN_INVALID_CODE);
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
            ResponseUtil.respondOk(event, Map.of("authenticated", false));
            return;
        }
        AuthRepository.findSessionUserByTokenHash(databaseRuntime.dataSource(), AuthUtil.hashToken(sessionToken.get()))
            .ifPresentOrElse(
                sessionUser -> ResponseUtil.respondOk(event, AuthUtil.sessionPayload(true, sessionUser)),
                () -> ResponseUtil.respondOk(event, Map.of("authenticated", false))
            );
    }

    protected void handleProfileLookup(final Event<HttpObject, HttpObject> event) {
        final Optional<SessionUser> sessionUser = AuthUtil.currentSessionUser(event.payload(), databaseRuntime.dataSource());
        if (sessionUser.isEmpty()) {
            ResponseUtil.respondUnauthorized(event, AuthUtil.PROFILE_AUTH_REQUIRED_CODE);
            return;
        }

        AuthRepository.findProfileByUserId(databaseRuntime.dataSource(), sessionUser.get().id())
            .ifPresentOrElse(
                profile -> ResponseUtil.respondOk(event, Map.of("profile", AuthUtil.profilePayload(profile))),
                () -> ResponseUtil.respondNotFound(event, AuthUtil.PROFILE_NOT_FOUND_CODE)
            );
    }

    protected void handlePublicProfileLookup(final Event<HttpObject, HttpObject> event) {
        final Optional<String> publicId = AuthUtil.publicProfileId(event.payload());
        if (publicId.isEmpty()) {
            ResponseUtil.respondNotFound(event, AuthUtil.PROFILE_NOT_FOUND_CODE);
            return;
        }

        AuthRepository.findPublicProfileByPublicId(databaseRuntime.dataSource(), publicId.get())
            .ifPresentOrElse(
                profile -> ResponseUtil.respondOk(event, Map.of("profile", AuthUtil.publicProfilePayload(profile))),
                () -> {
                    if (AuthRepository.isDeletedPublicProfile(databaseRuntime.dataSource(), publicId.get())) {
                        ResponseUtil.respondJson(event, 410, Map.of("code", "USER_DELETED"));
                        return;
                    }
                    ResponseUtil.respondNotFound(event, AuthUtil.PROFILE_NOT_FOUND_CODE);
                }
            );
    }

    protected void handleProfileUpdate(final Event<HttpObject, HttpObject> event) {
        final Optional<SessionUser> sessionUser = AuthUtil.currentSessionUser(event.payload(), databaseRuntime.dataSource());
        if (sessionUser.isEmpty()) {
            ResponseUtil.respondUnauthorized(event, AuthUtil.PROFILE_UPDATE_AUTH_REQUIRED_CODE);
            return;
        }

        final LinkedTypeMap body = AuthUtil.bodyAsMap(event.payload());
        final String displayName = safeTrim(body.asString("displayName"));
        final String bio = safeTrim(body.asString("bio"));
        final boolean emailPublic = safeBoolean(body.get("emailPublic"));

        final Optional<String> validation = validateEditableProfileInput(displayName, bio);
        if (validation.isPresent()) {
            ResponseUtil.respondBadRequest(event, validation.get());
            return;
        }

        final UserProfile profile = AuthRepository.updateProfile(
            databaseRuntime.dataSource(),
            sessionUser.get().id(),
            displayName,
            bio,
            emailPublic
        );
        ResponseUtil.respondOk(event, Map.of("profile", AuthUtil.profilePayload(profile)));
    }

    protected void handleLogout(final Event<HttpObject, HttpObject> event) {
        AuthUtil.readSessionToken(event.payload())
            .ifPresent(token -> AuthRepository.deleteSession(databaseRuntime.dataSource(), AuthUtil.hashToken(token)));
        AuthUtil.respondLogout(event, AuthUtil.clearedSessionCookie(event.payload()));
    }

    protected void handleAccountDelete(final Event<HttpObject, HttpObject> event) {
        final Optional<SessionUser> sessionUser = AuthUtil.currentSessionUser(event.payload(), databaseRuntime.dataSource());
        if (sessionUser.isEmpty()) {
            ResponseUtil.respondUnauthorized(event, AuthUtil.ACCOUNT_DELETE_AUTH_REQUIRED_CODE);
            return;
        }
        AuthRepository.deleteAccount(databaseRuntime.dataSource(), sessionUser.get().id());
        AuthUtil.respondLogout(event, AuthUtil.clearedSessionCookie(event.payload()));
    }

    protected void handleVerifyEmailRequest(final Event<HttpObject, HttpObject> event) {
        final Optional<SessionUser> sessionUser = AuthUtil.currentSessionUser(event.payload(), databaseRuntime.dataSource());
        if (sessionUser.isEmpty()) {
            ResponseUtil.respondUnauthorized(event, AuthUtil.VERIFICATION_AUTH_REQUIRED_CODE);
            return;
        }
        if (sessionUser.get().emailVerified()) {
            ResponseUtil.respondOk(event, Map.of("sent", false, "alreadyVerified", true));
            return;
        }
        try {
            if (!resendVerificationEmail(sessionUser.get())) {
                ResponseUtil.respondTooManyRequests(event, AuthUtil.VERIFICATION_DAILY_LIMIT_CODE);
                return;
            }
            ResponseUtil.respondOk(event, Map.of("sent", true, "alreadyVerified", false));
        } catch (RuntimeException exception) {
            context.warn(() -> "Unable to resend verification email for {}", sessionUser.get().email());
            ResponseUtil.respondServerError(event, AuthUtil.VERIFICATION_SEND_FAILED_CODE);
        }
    }

    protected void handleVerifyEmailConfirm(final Event<HttpObject, HttpObject> event) {
        final LinkedTypeMap body = AuthUtil.bodyAsMap(event.payload());
        final String token = safeTrim(body.asString("token"));
        if (token.isBlank()) {
            ResponseUtil.respondBadRequest(event, AuthUtil.VERIFICATION_TOKEN_REQUIRED_CODE);
            return;
        }
        final boolean confirmed = confirmVerificationEmail(token);
        if (!confirmed) {
            ResponseUtil.respondBadRequest(event, AuthUtil.VERIFICATION_TOKEN_INVALID_CODE);
            return;
        }
        ResponseUtil.respondOk(event, Map.of("verified", true));
    }

    protected void handlePasswordResetRequest(final Event<HttpObject, HttpObject> event) {
        final LinkedTypeMap body = AuthUtil.bodyAsMap(event.payload());
        final String email = AuthUtil.normalizeEmail(body.asString("email"));
        if (email.isBlank()) {
            ResponseUtil.respondBadRequest(event, AuthUtil.PASSWORD_RESET_EMAIL_REQUIRED_CODE);
            return;
        }

        final Optional<SessionUser> recipient = AuthRepository.findPasswordResetRecipientByEmail(databaseRuntime.dataSource(), email);
        if (recipient.isEmpty()) {
            ResponseUtil.respondOk(event, Map.of("requested", true));
            return;
        }

        try {
            issuePasswordResetEmail(recipient.get());
            ResponseUtil.respondOk(event, Map.of("requested", true));
        } catch (RuntimeException exception) {
            context.warn(() -> "Unable to send password reset email for {}", email);
            ResponseUtil.respondServerError(event, AuthUtil.PASSWORD_RESET_SEND_FAILED_CODE);
        }
    }

    protected void handlePasswordResetConfirm(final Event<HttpObject, HttpObject> event) {
        final LinkedTypeMap body = AuthUtil.bodyAsMap(event.payload());
        final String token = safeTrim(body.asString("token"));
        final String password = body.asString("password");
        if (token.isBlank()) {
            ResponseUtil.respondBadRequest(event, AuthUtil.PASSWORD_RESET_TOKEN_REQUIRED_CODE);
            return;
        }
        if (!AuthUtil.meetsPasswordRequirements(password)) {
            ResponseUtil.respondBadRequest(event, AuthUtil.PASSWORD_REQUIREMENTS_CODE);
            return;
        }
        if (!resetPassword(token, password)) {
            ResponseUtil.respondBadRequest(event, AuthUtil.PASSWORD_RESET_TOKEN_INVALID_CODE);
            return;
        }
        ResponseUtil.respondOk(event, Map.of("reset", true));
    }

    private void issueInitialVerificationEmail(final SessionUser sessionUser) {
        issueVerificationEmail(sessionUser);
    }

    private boolean resendVerificationEmail(final SessionUser sessionUser) {
        return issueVerificationEmail(sessionUser);
    }

    private boolean confirmVerificationEmail(final String token) {
        return AuthRepository.confirmEmailVerification(databaseRuntime.dataSource(), AuthUtil.hashToken(token));
    }

    private void issuePasswordResetEmail(final SessionUser recipient) {
        final String token = AuthUtil.newPasswordResetToken();
        AuthRepository.createPasswordResetToken(
            databaseRuntime.dataSource(),
            recipient.id(),
            AuthUtil.hashToken(token),
            Instant.now().plus(AuthUtil.PASSWORD_RESET_TTL)
        );
        transactionalEmailSender.sendPasswordResetEmail(
            recipient.email(),
            recipient.displayName(),
            AuthUtil.passwordResetUrl(emailVerificationSettings.publicBaseUrl(), token)
        );
    }

    private boolean resetPassword(final String token, final String password) {
        return AuthRepository.resetPassword(
            databaseRuntime.dataSource(),
            AuthUtil.hashToken(token),
            AuthUtil.hashPassword(password)
        );
    }

    private boolean issueVerificationEmail(final SessionUser sessionUser) {
        final Instant now = Instant.now();
        final String token = AuthUtil.newEmailVerificationToken();
        final String tokenHash = AuthUtil.hashToken(token);
        final boolean allowed = AuthRepository.beginEmailVerificationSendAttempt(
            databaseRuntime.dataSource(),
            sessionUser.id(),
            tokenHash,
            now.plus(AuthUtil.EMAIL_VERIFICATION_TTL),
            now.minus(24, ChronoUnit.HOURS),
            MAX_VERIFICATION_EMAILS_PER_24_HOURS
        );
        if (!allowed) {
            return false;
        }

        try {
            transactionalEmailSender.sendVerificationEmail(
                sessionUser.email(),
                sessionUser.displayName(),
                AuthUtil.emailVerificationUrl(emailVerificationSettings.publicBaseUrl(), token)
            );
            AuthRepository.completeEmailVerificationSendAttempt(
                databaseRuntime.dataSource(),
                sessionUser.id(),
                tokenHash
            );
            return true;
        } catch (RuntimeException exception) {
            AuthRepository.abortEmailVerificationSendAttempt(databaseRuntime.dataSource(), tokenHash);
            throw exception;
        }
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
            return Optional.of(AuthUtil.EMAIL_REQUIRED_CODE);
        }
        if (email.length() > AuthUtil.EMAIL_MAX_LENGTH) {
            return Optional.of(AuthUtil.EMAIL_TOO_LONG_CODE);
        }
        if (!email.contains("@")) {
            return Optional.of(AuthUtil.EMAIL_INVALID_CODE);
        }
        return Optional.empty();
    }

    private static Optional<String> validateEditableProfileInput(final String displayName, final String bio) {
        if (displayName.length() < AuthUtil.DISPLAY_NAME_MIN_LENGTH || displayName.length() > AuthUtil.DISPLAY_NAME_MAX_LENGTH) {
            return Optional.of(AuthUtil.DISPLAY_NAME_INVALID_CODE);
        }
        if (bio.length() > AuthUtil.BIO_MAX_LENGTH) {
            return Optional.of(AuthUtil.BIO_TOO_LONG_CODE);
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
