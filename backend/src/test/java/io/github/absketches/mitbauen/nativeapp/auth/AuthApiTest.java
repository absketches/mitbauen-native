package io.github.absketches.mitbauen.nativeapp.auth;

import berlin.yuna.typemap.model.LinkedTypeMap;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.TestDatabaseMigrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.HttpObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApiTest {

    private static final String OPEN_INVITE = "test-open-invite";
    private static final String PRIMARY_EMAIL = "builder.one@example.test";
    private static final String PRIMARY_DISPLAY_NAME = "Alex Builder";
    private static final String PRIMARY_PASSWORD = "SuperSafe1";

    private Nano nano;
    private DatabaseRuntime databaseRuntime;

    @AfterEach
    void tearDown() {
        nano.stop(AuthApiTest.class).waitForStop();
        if (databaseRuntime != null) {
            databaseRuntime.stop();
            databaseRuntime = null;
        }
    }

    @Test
    void registersWithTheOpenInviteWithoutReturningInviteToken() {
        nano = newTestNano();

        final HttpObject registerResponse = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", PRIMARY_EMAIL,
            "displayName", PRIMARY_DISPLAY_NAME,
            "password", PRIMARY_PASSWORD
        ));

        assertThat(registerResponse.statusCode()).isEqualTo(201);
        assertThat(cookieValue(registerResponse, AuthUtil.AUTH_SESSION_COOKIE)).isNotBlank();

        final LinkedTypeMap registerBody = registerResponse.bodyAsMap();
        assertThat(registerBody.asBoolean("authenticated")).isTrue();
        assertThat(registerBody.asMap("user").asString("email")).isEqualTo(PRIMARY_EMAIL);
        assertThat(registerBody.asMap("user").asString("inviteToken")).isNull();
    }

    @Test
    void acceptsDifferentEmailsForTheSharedInvite() {
        nano = newTestNano();

        final HttpObject firstRegister = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", "someone@example.com",
            "displayName", "First Person",
            "password", PRIMARY_PASSWORD
        ));

        final HttpObject secondRegister = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", "another@example.com",
            "displayName", "Second Person",
            "password", PRIMARY_PASSWORD
        ));

        assertThat(firstRegister.statusCode()).isEqualTo(201);
        assertThat(secondRegister.statusCode()).isEqualTo(201);
    }

    @Test
    void reusesAUserInviteLinkForMultipleRegistrations() {
        nano = newTestNano();

        final HttpObject firstRegister = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", PRIMARY_EMAIL,
            "displayName", PRIMARY_DISPLAY_NAME,
            "password", PRIMARY_PASSWORD
        ));
        assertThat(firstRegister.statusCode()).isEqualTo(201);

        final HttpObject secondRegister = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", "friend-one@example.com",
            "displayName", "Friend One",
            "password", "FriendOne1"
        ));

        final HttpObject thirdRegister = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", "friend-two@example.com",
            "displayName", "Friend Two",
            "password", "FriendTwo1"
        ));

        assertThat(secondRegister.statusCode()).isEqualTo(201);
        assertThat(thirdRegister.statusCode()).isEqualTo(201);
    }

    @Test
    void logsInCreatesSessionAndLogsOut() {
        nano = newTestNano();

        post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", PRIMARY_EMAIL,
            "displayName", PRIMARY_DISPLAY_NAME,
            "password", PRIMARY_PASSWORD
        ));

        final HttpObject loginResponse = post("/api/auth/login", Map.of(
            "email", PRIMARY_EMAIL,
            "password", PRIMARY_PASSWORD
        ));

        assertThat(loginResponse.statusCode()).isEqualTo(200);
        final String sessionCookie = cookieValue(loginResponse, AuthUtil.AUTH_SESSION_COOKIE);
        assertThat(sessionCookie).isNotBlank();

        final HttpObject sessionResponse = get("/api/auth/session", sessionCookie);
        assertThat(sessionResponse.statusCode()).isEqualTo(200);
        assertThat(sessionResponse.bodyAsMap().asBoolean("authenticated")).isTrue();

        final HttpObject logoutResponse = post("/api/auth/logout", Map.of(), sessionCookie);
        assertThat(logoutResponse.statusCode()).isEqualTo(200);

        final HttpObject sessionAfterLogout = get("/api/auth/session", sessionCookie);
        assertThat(sessionAfterLogout.bodyAsMap().asBoolean("authenticated")).isFalse();
    }

    @Test
    void authenticatedRequestsUpdateSessionLastSeenAt() throws Exception {
        nano = newTestNano();

        final HttpObject loginResponse = registerPrimaryUser();
        final String sessionCookie = cookieValue(loginResponse, AuthUtil.AUTH_SESSION_COOKIE);
        final Instant initialLastSeenAt = sessionLastSeenAt(sessionCookie);

        TimeUnit.MILLISECONDS.sleep(1100);

        final HttpObject sessionResponse = get("/api/auth/session", sessionCookie);
        assertThat(sessionResponse.statusCode()).isEqualTo(200);
        assertThat(sessionLastSeenAt(sessionCookie)).isAfter(initialLastSeenAt);
    }

    @Test
    void readsAndUpdatesTheAuthenticatedUserProfile() {
        nano = newTestNano();

        final HttpObject registerResponse = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", PRIMARY_EMAIL,
            "emailPublic", true,
            "displayName", PRIMARY_DISPLAY_NAME,
            "bio", "Building the first contributor path for neighborhood projects.",
            "password", PRIMARY_PASSWORD
        ));
        assertThat(registerResponse.statusCode()).isEqualTo(201);
        final String sessionCookie = cookieValue(registerResponse, AuthUtil.AUTH_SESSION_COOKIE);

        final HttpObject initialProfile = get("/api/profile", sessionCookie);
        assertThat(initialProfile.statusCode()).isEqualTo(200);
        final LinkedTypeMap initialBody = initialProfile.bodyAsMap().asMap("profile");
        assertThat(initialBody.asString("displayName")).isEqualTo(PRIMARY_DISPLAY_NAME);
        assertThat(initialBody.asString("bio")).contains("contributor path");
        assertThat(initialBody.asString("email")).isEqualTo(PRIMARY_EMAIL);
        assertThat(initialBody.asBoolean("emailPublic")).isTrue();

        final HttpObject updateResponse = put("/api/profile", Map.of(
            "displayName", "Alex Builder Updated",
            "bio", "Now focusing on trust, onboarding, and the first project handoffs.",
            "email", "changed@example.test",
            "emailPublic", false
        ), sessionCookie);

        assertThat(updateResponse.statusCode()).isEqualTo(200);
        final LinkedTypeMap updatedProfile = updateResponse.bodyAsMap().asMap("profile");
        assertThat(updatedProfile.asString("displayName")).isEqualTo("Alex Builder Updated");
        assertThat(updatedProfile.asString("bio")).contains("first project handoffs");
        assertThat(updatedProfile.asString("email")).isEqualTo(PRIMARY_EMAIL);
        assertThat(updatedProfile.asBoolean("emailPublic")).isFalse();
    }

    @Test
    void exposesPublicProfilesByOpaquePublicIdAndOnlyShowsEmailWhenAllowed() {
        nano = newTestNano();

        final HttpObject firstRegister = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", PRIMARY_EMAIL,
            "displayName", PRIMARY_DISPLAY_NAME,
            "bio", "Building the first contributor path for neighborhood projects.",
            "emailPublic", false,
            "password", PRIMARY_PASSWORD
        ));
        assertThat(firstRegister.statusCode()).isEqualTo(201);
        final String firstPublicId = userPublicIdByEmail(PRIMARY_EMAIL);

        final HttpObject privateProfile = get("/api/users/" + firstPublicId, null);
        assertThat(privateProfile.statusCode()).isEqualTo(200);
        final LinkedTypeMap privateBody = privateProfile.bodyAsMap().asMap("profile");
        assertThat(privateBody.asString("displayName")).isEqualTo(PRIMARY_DISPLAY_NAME);
        assertThat(privateBody.asString("bio")).contains("contributor path");
        assertThat(privateBody.asString("email")).isNull();

        final HttpObject secondRegister = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", "public.builder@example.test",
            "displayName", "Public Builder",
            "bio", "Happy to be reachable by collaborators.",
            "emailPublic", true,
            "password", PRIMARY_PASSWORD
        ));
        assertThat(secondRegister.statusCode()).isEqualTo(201);
        final String secondPublicId = userPublicIdByEmail("public.builder@example.test");

        final HttpObject publicProfile = get("/api/users/" + secondPublicId, null);
        assertThat(publicProfile.statusCode()).isEqualTo(200);
        final LinkedTypeMap publicBody = publicProfile.bodyAsMap().asMap("profile");
        assertThat(publicBody.asString("email")).isEqualTo("public.builder@example.test");
    }

    @Test
    void validatesInviteToken() {
        nano = newTestNano();

        final HttpObject validResponse = new HttpObject()
            .path(baseUrl("/api/invites/validate?token=" + OPEN_INVITE))
            .send(nano.context(AuthApiTest.class));

        final HttpObject invalidResponse = new HttpObject()
            .path(baseUrl("/api/invites/validate?token=unknown"))
            .send(nano.context(AuthApiTest.class));

        assertThat(validResponse.bodyAsMap().asBoolean("valid")).isTrue();
        assertThat(invalidResponse.bodyAsMap().asBoolean("valid")).isFalse();
    }

    @Test
    void rejectsWeakPasswordsDuringRegistration() {
        nano = newTestNano();

        final HttpObject registerResponse = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", PRIMARY_EMAIL,
            "displayName", PRIMARY_DISPLAY_NAME,
            "password", "password"
        ));

        assertThat(registerResponse.statusCode()).isEqualTo(400);
        assertThat(registerResponse.bodyAsMap().asString("error"))
            .isEqualTo(AuthUtil.PASSWORD_REQUIREMENTS_MESSAGE);
    }

    @Test
    void rejectsDuplicateEmailDuringRegistration() {
        nano = newTestNano();

        final HttpObject firstRegister = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", PRIMARY_EMAIL,
            "displayName", PRIMARY_DISPLAY_NAME,
            "password", PRIMARY_PASSWORD
        ));
        assertThat(firstRegister.statusCode()).isEqualTo(201);

        final HttpObject duplicateRegister = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", PRIMARY_EMAIL,
            "displayName", PRIMARY_DISPLAY_NAME + " Again",
            "password", PRIMARY_PASSWORD
        ));

        assertThat(duplicateRegister.statusCode()).isEqualTo(409);
        assertThat(duplicateRegister.bodyAsMap().asString("error"))
            .isEqualTo("An account already exists for that email.");
    }

    private Nano newTestNano() {
        final String jdbcUrl = "jdbc:h2:mem:mitbauen_auth_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        TestDatabaseMigrations.migrate(jdbcUrl, "sa", "");
        TestDatabaseMigrations.seedInvite(jdbcUrl, "sa", "", OPEN_INVITE);
        databaseRuntime = new DatabaseRuntime(jdbcUrl, "sa", "", "mitbauen-test-auth");
        return new Nano(
            Map.of(
                HttpServer.CONFIG_SERVICE_HTTP_PORT, 0
            ),
            new HttpServer(),
            new HttpClient(),
            new AuthService(databaseRuntime)
        );
    }

    private HttpObject registerPrimaryUser() {
        final HttpObject registerResponse = post("/api/auth/register", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", PRIMARY_EMAIL,
            "displayName", PRIMARY_DISPLAY_NAME,
            "password", PRIMARY_PASSWORD
        ));
        assertThat(registerResponse.statusCode()).isEqualTo(201);
        return registerResponse;
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

    private HttpObject put(final String path, final Map<String, Object> body, final String sessionCookie) {
        final HttpObject request = new HttpObject()
            .path(baseUrl(path))
            .methodType("PUT")
            .contentType("application/json")
            .body(body);
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(AuthApiTest.class));
    }

    private String baseUrl(final String path) {
        return "http://localhost:" + nano.service(HttpServer.class).port() + path;
    }

    private Instant sessionLastSeenAt(final String sessionCookie) {
        final String sql = """
            select last_seen_at
            from sessions
            where token_hash = ?
            """;
        try (Connection connection = databaseRuntime.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, AuthUtil.hashToken(sessionCookie));
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getTimestamp("last_seen_at").toInstant();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load session last_seen_at", exception);
        }
    }

    private String userPublicIdByEmail(final String email) {
        final String sql = """
            select public_id
            from users
            where email = ?
            """;
        try (Connection connection = databaseRuntime.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString("public_id");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load public_id", exception);
        }
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
