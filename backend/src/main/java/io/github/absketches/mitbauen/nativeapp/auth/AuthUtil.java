package io.github.absketches.mitbauen.nativeapp.auth;

import berlin.yuna.typemap.model.LinkedTypeMap;
import org.mindrot.jbcrypt.BCrypt;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class AuthUtil {

    public static final String AUTH_SESSION_COOKIE = "mitbauen_session";
    public static final Duration SESSION_TTL = Duration.ofDays(14);
    public static final String PASSWORD_REQUIREMENTS_MESSAGE =
        "Password must be at least 8 characters and include an uppercase letter, a lowercase letter, and a digit.";
    public static final String INVITE_VALIDATE_PATH = "/api/invites/validate";
    public static final String AUTH_REGISTER_PATH = "/api/auth/register";
    public static final String AUTH_LOGIN_PATH = "/api/auth/login";
    public static final String AUTH_LOGOUT_PATH = "/api/auth/logout";
    public static final String AUTH_SESSION_PATH = "/api/auth/session";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public sealed interface RoutesMatch permits InviteValidateRoute, RegisterRoute, LoginRoute, LogoutRoute, SessionRoute, NoMatch {
    }

    public record InviteValidateRoute() implements RoutesMatch {
    }

    public record RegisterRoute() implements RoutesMatch {
    }

    public record LoginRoute() implements RoutesMatch {
    }

    public record LogoutRoute() implements RoutesMatch {
    }

    public record SessionRoute() implements RoutesMatch {
    }

    public record NoMatch() implements RoutesMatch {
    }

    private AuthUtil() {
    }

    public static RoutesMatch match(final HttpObject request) {
        if (request.pathMatch(INVITE_VALIDATE_PATH)) {
            return new InviteValidateRoute();
        }
        if (request.pathMatch(AUTH_REGISTER_PATH)) {
            return new RegisterRoute();
        }
        if (request.pathMatch(AUTH_LOGIN_PATH)) {
            return new LoginRoute();
        }
        if (request.pathMatch(AUTH_LOGOUT_PATH)) {
            return new LogoutRoute();
        }
        if (request.pathMatch(AUTH_SESSION_PATH)) {
            return new SessionRoute();
        }
        return new NoMatch();
    }

    public static String normalizeEmail(final String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    public static String handleFrom(final String displayName, final String normalizedEmail) {
        final String candidate = (displayName == null || displayName.isBlank()) ? normalizedEmail : displayName;
        final String slug = candidate.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "member" : slug;
    }

    public static String hashPassword(final String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    public static boolean verifyPassword(final String password, final String passwordHash) {
        return BCrypt.checkpw(password, passwordHash);
    }

    public static boolean meetsPasswordRequirements(final String password) {
        return password != null
            && password.length() >= 8
            && password.chars().anyMatch(Character::isUpperCase)
            && password.chars().anyMatch(Character::isLowerCase)
            && password.chars().anyMatch(Character::isDigit);
    }

    public static String newSessionToken() {
        return "sess_" + randomToken(24);
    }

    public static String newInviteToken() {
        return "invite_" + randomToken(18);
    }

    public static String hashToken(final String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            final StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte next : hash) {
                builder.append(String.format("%02x", next));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-256 algorithm", exception);
        }
    }

    public static Optional<String> readSessionToken(final HttpObject request) {
        final String cookieHeader = request.header("cookie");
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return Optional.empty();
        }
        final String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            final String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && AUTH_SESSION_COOKIE.equals(parts[0].trim())) {
                return Optional.of(parts[1].trim());
            }
        }
        return Optional.empty();
    }

    public static String sessionCookie(final HttpObject request, final String token) {
        return cookieValue(request, token, SESSION_TTL.toSeconds());
    }

    public static String clearedSessionCookie(final HttpObject request) {
        return cookieValue(request, "", 0);
    }

    public static LinkedTypeMap bodyAsMap(final HttpObject request) {
        return request.bodyAsMap();
    }

    public static void respondInviteValidation(final Event<HttpObject, HttpObject> event, final InviteLink inviteLink) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", true);
        payload.put("allowedEmail", inviteLink.allowedEmail());
        event.payload().createCorsResponse()
            .statusCode(200)
            .body(payload)
            .respond(event);
    }

    public static void respondInvalidInvite(final Event<HttpObject, HttpObject> event) {
        event.payload().createCorsResponse()
            .statusCode(200)
            .body(Map.of("valid", false))
            .respond(event);
    }

    public static void respondAuthSuccess(
        final Event<HttpObject, HttpObject> event,
        final SessionUser sessionUser,
        final String sessionCookie,
        final int statusCode
    ) {
        final HttpObject response = event.payload().createCorsResponse()
            .statusCode(statusCode)
            .header("Set-Cookie", sessionCookie)
            .body(sessionPayload(true, sessionUser));
        response.respond(event);
    }

    public static void respondSession(final Event<HttpObject, HttpObject> event, final SessionUser sessionUser) {
        event.payload().createCorsResponse()
            .statusCode(200)
            .body(sessionPayload(true, sessionUser))
            .respond(event);
    }

    public static void respondAnonymousSession(final Event<HttpObject, HttpObject> event) {
        event.payload().createCorsResponse()
            .statusCode(200)
            .body(Map.of("authenticated", false))
            .respond(event);
    }

    public static void respondLogout(final Event<HttpObject, HttpObject> event, final String clearedCookie) {
        event.payload().createCorsResponse()
            .statusCode(200)
            .header("Set-Cookie", clearedCookie)
            .body(Map.of("authenticated", false))
            .respond(event);
    }

    public static void respondBadRequest(final Event<HttpObject, HttpObject> event, final String message) {
        event.payload().createCorsResponse()
            .statusCode(400)
            .body(Map.of("error", message))
            .respond(event);
    }

    public static void respondUnauthorized(final Event<HttpObject, HttpObject> event, final String message) {
        event.payload().createCorsResponse()
            .statusCode(401)
            .body(Map.of("error", message))
            .respond(event);
    }

    public static void respondForbidden(final Event<HttpObject, HttpObject> event, final String message) {
        event.payload().createCorsResponse()
            .statusCode(403)
            .body(Map.of("error", message))
            .respond(event);
    }

    public static void respondConflict(final Event<HttpObject, HttpObject> event, final String message) {
        event.payload().createCorsResponse()
            .statusCode(409)
            .body(Map.of("error", message))
            .respond(event);
    }

    public static void respondOptions(final Event<HttpObject, HttpObject> event) {
        event.payload().createCorsResponse().respond(event);
    }

    public static void respondMethodNotAllowed(final Event<HttpObject, HttpObject> event) {
        event.payload().createCorsResponse()
            .statusCode(405)
            .body(Map.of("error", "Method Not Allowed", "path", event.payload().path()))
            .respond(event);
    }

    public static void respondFailure(final Event<HttpObject, HttpObject> event, final Throwable error) {
        event.payload().createCorsResponse().failure(500, error).respond(event);
    }

    private static Map<String, Object> sessionPayload(final boolean authenticated, final SessionUser sessionUser) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("authenticated", authenticated);
        if (sessionUser != null) {
            final Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", sessionUser.id());
            user.put("displayName", sessionUser.displayName());
            user.put("email", sessionUser.email());
            user.put("inviteToken", sessionUser.inviteToken());
            payload.put("user", user);
        }
        return payload;
    }

    private static String cookieValue(final HttpObject request, final String token, final long maxAgeSeconds) {
        final boolean secure = "https".equalsIgnoreCase(request.protocol());
        final StringBuilder builder = new StringBuilder();
        builder.append(AUTH_SESSION_COOKIE).append("=").append(token)
            .append("; Path=/")
            .append("; Max-Age=").append(maxAgeSeconds)
            .append("; HttpOnly")
            .append("; SameSite=Lax");
        if (secure) {
            builder.append("; Secure");
        }
        return builder.toString();
    }

    private static String randomToken(final int byteCount) {
        final byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
