package io.github.absketches.mitbauen.nativeapp.comments;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import io.github.absketches.mitbauen.nativeapp.auth.AuthService;
import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.auth.EmailVerificationSettings;
import io.github.absketches.mitbauen.nativeapp.auth.VerificationEmailSender;
import io.github.absketches.mitbauen.nativeapp.comments.ProjectCommentsService;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.PostgresTestDatabase;
import io.github.absketches.mitbauen.nativeapp.db.TestDatabaseMigrations;
import io.github.absketches.mitbauen.nativeapp.notifications.NotificationsService;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectDiscussionApiTest {

    private static final String OPEN_INVITE = "test-open-invite";
    private static final String PRIMARY_PASSWORD = "SuperSafe1";
    private static final EmailVerificationSettings EMAIL_VERIFICATION_SETTINGS =
        new EmailVerificationSettings("https://www.mitbauen.space", "Mitbauen <no-reply@mail.mitbauen.space>", "");
    private static final VerificationEmailSender NOOP_VERIFICATION_EMAIL_SENDER =
        (recipientEmail, recipientName, verificationUrl) -> { };

    private Nano nano;
    private DatabaseRuntime databaseRuntime;

    @AfterEach
    void tearDown() {
        if (nano != null) {
            nano.stop(ProjectDiscussionApiTest.class).waitForStop();
            nano = null;
        }
        if (databaseRuntime != null) {
            databaseRuntime.stop();
            databaseRuntime = null;
        }
    }

    @Test
    void onlyVerifiedMembersCanReadProjectComments() {
        nano = newTestNano();
        final String ownerCookie = registerAndReturnSessionCookie("owner.comments@example.test", "Owner Comments");
        final String slug = createProject(ownerCookie, "Member Discussion Hub");
        final String unverifiedCookie = registerAndReturnSessionCookie("unverified.comments@example.test", "Unverified Comments", false);

        final HttpObject anonymousResponse = sendGet("/api/projects/" + slug + "/comments", null);
        assertThat(anonymousResponse.statusCode()).isEqualTo(401);

        final HttpObject unverifiedResponse = sendGet("/api/projects/" + slug + "/comments", unverifiedCookie);
        assertThat(unverifiedResponse.statusCode()).isEqualTo(403);

        final HttpObject verifiedResponse = sendGet("/api/projects/" + slug + "/comments", ownerCookie);
        assertThat(verifiedResponse.statusCode()).isEqualTo(200);
        assertThat(verifiedResponse.bodyAsMap().asList("comments")).isEmpty();
    }

    @Test
    void verifiedMembersCanPostTrimmedComments() {
        nano = newTestNano();
        final String ownerCookie = registerAndReturnSessionCookie("owner.post@example.test", "Owner Post");
        final String slug = createProject(ownerCookie, "Verified Comment Board");
        final String commenterCookie = registerAndReturnSessionCookie("commenter.post@example.test", "Commenter Post");

        final HttpObject response = sendJson("/api/projects/" + slug + "/comments", "POST", Map.of(
            "body", "  This project discussion is now active.  "
        ), commenterCookie);

        assertThat(response.statusCode()).isEqualTo(201);
        final LinkedTypeMap comment = response.bodyAsMap().asMap("comment");
        assertThat(comment.asString("body")).isEqualTo("This project discussion is now active.");
        assertThat(comment.asString("authorDisplayName")).isEqualTo("Commenter Post");

        final TypeList comments = sendGet("/api/projects/" + slug + "/comments", ownerCookie).bodyAsMap().asList("comments");
        assertThat(comments).hasSize(1);
    }

    @Test
    void commentNotificationsAreGroupedAndClearedWhenRead() {
        nano = newTestNano();
        final String ownerCookie = registerAndReturnSessionCookie("owner.notify@example.test", "Owner Notify");
        final String slug = createProject(ownerCookie, "Notification Garden");
        final String commenterCookie = registerAndReturnSessionCookie("commenter.notify@example.test", "Commenter Notify");

        sendJson("/api/projects/" + slug + "/comments", "POST", Map.of("body", "First unread comment."), commenterCookie);
        sendJson("/api/projects/" + slug + "/comments", "POST", Map.of("body", "Second unread comment."), commenterCookie);

        final HttpObject notificationsResponse = sendGet("/api/notifications", ownerCookie);
        assertThat(notificationsResponse.statusCode()).isEqualTo(200);
        assertThat(notificationsResponse.header("access-control-allow-credentials")).isEqualTo("true");
        final TypeList notifications = notificationsResponse.bodyAsMap().asList("notifications");
        assertThat(notifications).hasSize(1);
        final LinkedTypeMap notification = new LinkedTypeMap((Map<?, ?>) notifications.get(0));
        assertThat(notification.asString("type")).isEqualTo("project_comment");
        assertThat(notification.asString("projectSlug")).isEqualTo(slug);
        assertThat(notification.asString("projectTitle")).isEqualTo("Notification Garden");
        assertThat(notification.asString("actorName")).isEqualTo("Commenter Notify");
        assertThat(notification.asString("latestBody")).isEqualTo("Second unread comment.");
        assertThat(((Number) notification.get("unreadCount")).longValue()).isEqualTo(2);

        final HttpObject readResponse = sendJson("/api/projects/" + slug + "/comments/read", "POST", Map.of(), ownerCookie);
        assertThat(readResponse.statusCode()).isEqualTo(200);

        final TypeList clearedNotifications = sendGet("/api/notifications", ownerCookie).bodyAsMap().asList("notifications");
        assertThat(clearedNotifications).isEmpty();
    }

    @Test
    void priorCommentersReceiveNotificationsForLaterComments() {
        nano = newTestNano();
        final String ownerCookie = registerAndReturnSessionCookie("owner.prior@example.test", "Owner Prior");
        final String slug = createProject(ownerCookie, "Prior Commenters Project");
        final String firstCommenterCookie = registerAndReturnSessionCookie("first.prior@example.test", "First Prior");
        final String secondCommenterCookie = registerAndReturnSessionCookie("second.prior@example.test", "Second Prior");

        sendJson("/api/projects/" + slug + "/comments", "POST", Map.of("body", "I am following this discussion."), firstCommenterCookie);
        sendJson("/api/projects/" + slug + "/comments/read", "POST", Map.of(), firstCommenterCookie);
        sendJson("/api/projects/" + slug + "/comments", "POST", Map.of("body", "A later update for prior commenters."), secondCommenterCookie);

        final TypeList notifications = sendGet("/api/notifications", firstCommenterCookie).bodyAsMap().asList("notifications");
        assertThat(notifications).hasSize(1);
        final LinkedTypeMap notification = new LinkedTypeMap((Map<?, ?>) notifications.get(0));
        assertThat(notification.asString("actorName")).isEqualTo("Second Prior");
        assertThat(notification.asString("latestBody")).isEqualTo("A later update for prior commenters.");

        final TypeList ownNotifications = sendGet("/api/notifications", secondCommenterCookie).bodyAsMap().asList("notifications");
        assertThat(ownNotifications).isEmpty();
    }

    @Test
    void projectsAndCommentsRemainAfterAccountDeletion() {
        nano = newTestNano();
        final String ownerCookie = registerAndReturnSessionCookie("owner.deleted@example.test", "Owner Deleted");
        final String slug = createProject(ownerCookie, "Deleted Owner Project");

        final HttpObject commentResponse = sendJson("/api/projects/" + slug + "/comments", "POST", Map.of(
            "body", "This comment should remain after account deletion."
        ), ownerCookie);
        assertThat(commentResponse.statusCode()).isEqualTo(201);

        final HttpObject deleteResponse = sendDelete("/api/profile", ownerCookie);
        assertThat(deleteResponse.statusCode()).isEqualTo(200);

        final String memberCookie = registerAndReturnSessionCookie("reader.deleted@example.test", "Reader Deleted");
        final HttpObject projectResponse = sendGet("/api/projects/" + slug, memberCookie);
        assertThat(projectResponse.statusCode()).isEqualTo(200);
        assertThat(projectResponse.bodyAsMap().asMap("project").asMap("founder").asString("name")).isEqualTo("Owner Deleted");

        final TypeList comments = sendGet("/api/projects/" + slug + "/comments", memberCookie).bodyAsMap().asList("comments");
        assertThat(comments).hasSize(1);
        final LinkedTypeMap comment = new LinkedTypeMap((Map<?, ?>) comments.get(0));
        assertThat(comment.asString("body")).isEqualTo("This comment should remain after account deletion.");
        assertThat(comment.asString("authorDisplayName")).isEqualTo("Owner Deleted");
    }

    private Nano newTestNano() {
        final PostgresTestDatabase.DatabaseConfig databaseConfig = PostgresTestDatabase.createDatabase("comments");
        TestDatabaseMigrations.migrate(databaseConfig.jdbcUrl(), databaseConfig.jdbcUser(), databaseConfig.jdbcPassword());
        TestDatabaseMigrations.seedInvite(databaseConfig.jdbcUrl(), databaseConfig.jdbcUser(), databaseConfig.jdbcPassword(), OPEN_INVITE);
        databaseRuntime = new DatabaseRuntime(
            databaseConfig.jdbcUrl(),
            databaseConfig.jdbcUser(),
            databaseConfig.jdbcPassword(),
            "mitbauen-test-comments"
        );
        return new Nano(
            Map.of(
                HttpServer.CONFIG_SERVICE_HTTP_PORT, 0
            ),
            new HttpServer(),
            new HttpClient(),
            new AuthService(databaseRuntime, EMAIL_VERIFICATION_SETTINGS, NOOP_VERIFICATION_EMAIL_SENDER),
            new ProjectFeedService(databaseRuntime),
            new ProjectCommentsService(databaseRuntime),
            new NotificationsService(databaseRuntime)
        );
    }

    private String createProject(final String ownerCookie, final String title) {
        return sendJson("/api/projects", "POST", Map.of(
            "title", title,
            "descriptions", Map.of("en", "A detailed project description for testing the comment and notification surface with enough detail to satisfy validation rules."),
            "founderRole", "Founder + Coordinator",
            "founderCommitment", "I am coordinating the project, publishing updates, and keeping collaborators aligned every week.",
            "openRoles", List.of(Map.of("title", "Collaborator", "commitment", "Help shape the next iteration."))
        ), ownerCookie).bodyAsMap().asString("slug");
    }

    private String registerAndReturnSessionCookie(final String email, final String displayName) {
        return registerAndReturnSessionCookie(email, displayName, true);
    }

    private String registerAndReturnSessionCookie(final String email, final String displayName, final boolean markEmailVerified) {
        final HttpObject response = sendJson("/api/auth/register", "POST", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", email,
            "displayName", displayName,
            "password", PRIMARY_PASSWORD
        ), null);
        assertThat(response.statusCode()).isEqualTo(201);
        if (markEmailVerified) {
            markEmailVerified(email);
        }
        return cookieValue(response, AuthUtil.AUTH_SESSION_COOKIE);
    }

    private HttpObject sendGet(final String path, final String sessionCookie) {
        final HttpObject request = new HttpObject().path(baseUrl(path));
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(ProjectDiscussionApiTest.class));
    }

    private HttpObject sendJson(final String path, final String method, final Map<String, Object> body, final String sessionCookie) {
        final HttpObject request = new HttpObject()
            .path(baseUrl(path))
            .methodType(method)
            .contentType("application/json")
            .body(body);
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(ProjectDiscussionApiTest.class));
    }

    private HttpObject sendDelete(final String path, final String sessionCookie) {
        final HttpObject request = new HttpObject()
            .path(baseUrl(path))
            .methodType("DELETE");
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(ProjectDiscussionApiTest.class));
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

    private void markEmailVerified(final String email) {
        final String sql = """
            update users
            set email_verified_at = current_timestamp
            where email = ?
            """;
        try (var connection = databaseRuntime.dataSource().getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to mark email verified for " + email, exception);
        }
    }
}
