package io.github.absketches.mitbauen.nativeapp.auth;

import berlin.yuna.typemap.model.LinkedTypeMap;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseService;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.TestDatabaseMigrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApiTest {

    private static final String BOOTSTRAP_INVITE = "basu-bootstrap-invite-2026";
    private static final String BOOTSTRAP_EMAIL = "basuabhi92@gmail.com";

    private Nano nano;

    @AfterEach
    void tearDown() {
        if (nano != null) {
            nano.stop(AuthApiTest.class).waitForStop();
        }
    }

    @Test
    void registersBootstrapUserAndCreatesInviteBacklink() {
        nano = newTestNano();

        final HttpObject registerResponse = post("/api/auth/register", Map.of(
            "inviteToken", BOOTSTRAP_INVITE,
            "email", BOOTSTRAP_EMAIL,
            "displayName", "Ab Basu",
            "password", "SuperSafe1"
        ));

        assertThat(registerResponse.statusCode()).isEqualTo(201);
        assertThat(cookieValue(registerResponse, AuthUtil.AUTH_SESSION_COOKIE)).isNotBlank();

        final LinkedTypeMap registerBody = registerResponse.bodyAsMap();
        assertThat(registerBody.asBoolean("authenticated")).isTrue();
        assertThat(registerBody.asMap("user").asString("email")).isEqualTo(BOOTSTRAP_EMAIL);
        assertThat(registerBody.asMap("user").asString("inviteToken")).isEqualTo(BOOTSTRAP_INVITE);
    }

    @Test
    void rejectsRegistrationWithWrongInviteEmail() {
        nano = newTestNano();

        final HttpObject registerResponse = post("/api/auth/register", Map.of(
            "inviteToken", BOOTSTRAP_INVITE,
            "email", "someone@example.com",
            "displayName", "Wrong Person",
            "password", "SuperSafe1"
        ));

        assertThat(registerResponse.statusCode()).isEqualTo(403);
        assertThat(registerResponse.bodyAsMap().asString("error"))
            .isEqualTo("This invite link is restricted to a different email address.");
    }

    @Test
    void reusesAUserInviteLinkForMultipleRegistrations() {
        nano = newTestNano();

        final HttpObject firstRegister = post("/api/auth/register", Map.of(
            "inviteToken", BOOTSTRAP_INVITE,
            "email", BOOTSTRAP_EMAIL,
            "displayName", "Ab Basu",
            "password", "SuperSafe1"
        ));
        assertThat(firstRegister.statusCode()).isEqualTo(201);
        final String reusableInvite = firstRegister.bodyAsMap().asMap("user").asString("inviteToken");
        assertThat(reusableInvite).isNotBlank();

        final HttpObject secondRegister = post("/api/auth/register", Map.of(
            "inviteToken", reusableInvite,
            "email", "friend-one@example.com",
            "displayName", "Friend One",
            "password", "FriendOne1"
        ));

        final HttpObject thirdRegister = post("/api/auth/register", Map.of(
            "inviteToken", reusableInvite,
            "email", "friend-two@example.com",
            "displayName", "Friend Two",
            "password", "FriendTwo1"
        ));

        assertThat(secondRegister.statusCode()).isEqualTo(201);
        assertThat(thirdRegister.statusCode()).isEqualTo(201);
        assertThat(secondRegister.bodyAsMap().asMap("user").asString("inviteToken")).isNull();
        assertThat(thirdRegister.bodyAsMap().asMap("user").asString("inviteToken")).isNull();
    }

    @Test
    void logsInCreatesSessionAndLogsOut() {
        nano = newTestNano();

        post("/api/auth/register", Map.of(
            "inviteToken", BOOTSTRAP_INVITE,
            "email", BOOTSTRAP_EMAIL,
            "displayName", "Ab Basu",
            "password", "SuperSafe1"
        ));

        final HttpObject loginResponse = post("/api/auth/login", Map.of(
            "email", BOOTSTRAP_EMAIL,
            "password", "SuperSafe1"
        ));

        assertThat(loginResponse.statusCode()).isEqualTo(200);
        assertThat(loginResponse.bodyAsMap().asMap("user").asString("inviteToken")).isEqualTo(BOOTSTRAP_INVITE);
        final String sessionCookie = cookieValue(loginResponse, AuthUtil.AUTH_SESSION_COOKIE);
        assertThat(sessionCookie).isNotBlank();

        final HttpObject sessionResponse = get("/api/auth/session", sessionCookie);
        assertThat(sessionResponse.statusCode()).isEqualTo(200);
        assertThat(sessionResponse.bodyAsMap().asBoolean("authenticated")).isTrue();
        assertThat(sessionResponse.bodyAsMap().asMap("user").asString("inviteToken")).isEqualTo(BOOTSTRAP_INVITE);

        final HttpObject logoutResponse = post("/api/auth/logout", Map.of(), sessionCookie);
        assertThat(logoutResponse.statusCode()).isEqualTo(200);

        final HttpObject sessionAfterLogout = get("/api/auth/session", sessionCookie);
        assertThat(sessionAfterLogout.bodyAsMap().asBoolean("authenticated")).isFalse();
    }

    @Test
    void validatesInviteToken() {
        nano = newTestNano();

        final HttpObject validResponse = new HttpObject()
            .path(baseUrl("/api/invites/validate?token=" + BOOTSTRAP_INVITE))
            .send(nano.context(AuthApiTest.class));

        final HttpObject invalidResponse = new HttpObject()
            .path(baseUrl("/api/invites/validate?token=unknown"))
            .send(nano.context(AuthApiTest.class));

        assertThat(validResponse.bodyAsMap().asBoolean("valid")).isTrue();
        assertThat(validResponse.bodyAsMap().asString("allowedEmail")).isEqualTo(BOOTSTRAP_EMAIL);
        assertThat(invalidResponse.bodyAsMap().asBoolean("valid")).isFalse();
    }

    @Test
    void rejectsWeakPasswordsDuringRegistration() {
        nano = newTestNano();

        final HttpObject registerResponse = post("/api/auth/register", Map.of(
            "inviteToken", BOOTSTRAP_INVITE,
            "email", BOOTSTRAP_EMAIL,
            "displayName", "Ab Basu",
            "password", "password"
        ));

        assertThat(registerResponse.statusCode()).isEqualTo(400);
        assertThat(registerResponse.bodyAsMap().asString("error"))
            .isEqualTo(AuthUtil.PASSWORD_REQUIREMENTS_MESSAGE);
    }

    private Nano newTestNano() {
        final String jdbcUrl = "jdbc:h2:mem:mitbauen_auth_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        TestDatabaseMigrations.migrate(jdbcUrl, "sa", "");
        final DatabaseRuntime databaseRuntime = new DatabaseRuntime("mitbauen-test-auth");
        return new Nano(
            Map.of(
                HttpServer.CONFIG_SERVICE_HTTP_PORT, 0,
                DatabaseService.CONFIG_JDBC_DATABASE_URL, jdbcUrl,
                DatabaseService.CONFIG_JDBC_DATABASE_USER, "sa",
                DatabaseService.CONFIG_JDBC_DATABASE_PASSWORD, ""
            ),
            new HttpServer(),
            new HttpClient(),
            new DatabaseService(databaseRuntime),
            new AuthService(databaseRuntime)
        );
    }

    private HttpObject post(final String path, final Map<String, Object> body) {
        return post(path, body, null);
    }

    private HttpObject post(final String path, final Map<String, Object> body, final String sessionCookie) {
        final HttpObject request = new HttpObject()
            .path(baseUrl(path))
            .methodType("POST")
            .contentType("application/json")
            .body(body);
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(AuthApiTest.class));
    }

    private HttpObject get(final String path, final String sessionCookie) {
        final HttpObject request = new HttpObject().path(baseUrl(path));
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(AuthApiTest.class));
    }

    private String baseUrl(final String path) {
        return "http://localhost:" + nano.service(HttpServer.class).port() + path;
    }

    private String cookieValue(final HttpObject response, final String cookieName) {
        final String setCookie = response.header("set-cookie");
        assertThat(setCookie).isNotBlank();
        final String prefix = cookieName + "=";
        final int start = setCookie.indexOf(prefix);
        assertThat(start).isGreaterThanOrEqualTo(0);
        final int valueStart = start + prefix.length();
        final int valueEnd = setCookie.indexOf(';', valueStart);
        return valueEnd >= 0 ? setCookie.substring(valueStart, valueEnd) : setCookie.substring(valueStart);
    }
}
