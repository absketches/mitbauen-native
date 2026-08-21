package io.github.absketches.mitbauen.nativeapp.jobs;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.auth.TransactionalEmailSender;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.PostgresTestDatabase;
import io.github.absketches.mitbauen.nativeapp.db.TestDatabaseMigrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.HttpObject;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobsApiTest {

    private Nano nano;
    private DatabaseRuntime databaseRuntime;
    private CapturingEmailSender emailSender;

    @AfterEach
    void tearDown() {
        if (nano != null) {
            nano.stop(JobsApiTest.class).waitForStop();
            nano = null;
        }
        if (databaseRuntime != null) {
            databaseRuntime.stop();
            databaseRuntime = null;
        }
    }

    @Test
    void listsOpenProjectRolesAsJobsForVerifiedMembers() {
        nano = newTestNano();
        final String sessionCookie = seedSession("owner.jobs@example.test", "Jobs Owner", true);
        seedProjectWithRoles(
            "neighborhood-repair-library",
            "Neighborhood Repair Library",
            "active",
            List.of(
                new RoleSeed("Founder + Repair Lead", "I host repair sessions.", true, false, 0),
                new RoleSeed("Tool Librarian", "Keep the catalog tidy and help neighbors find repair kits.", false, true, 1),
                new RoleSeed("Workshop Host", "Welcome visitors and keep repair afternoons organized.", false, true, 2),
                new RoleSeed("Closed Coordinator", "This closed role should not appear.", false, false, 3)
            )
        );
        seedProjectWithRoles(
            "dormant-tool-share",
            "Dormant Tool Share",
            "dormant",
            List.of(
                new RoleSeed("Founder", "I started this dormant project.", true, false, 0),
                new RoleSeed("Dormant Role", "This dormant role should not appear.", false, true, 1)
            )
        );
        seedProjectWithRoles(
            "deleted-owner-project",
            "Deleted Owner Project",
            "active",
            List.of(
                new RoleSeed("Founder", "I started this deleted-owner project.", true, false, 0),
                new RoleSeed("Invisible Role", "This deleted-owner role should not appear.", false, true, 1)
            )
        );
        markUserDeleted("deleted-owner-project@example.test");

        final HttpObject response = sendGet("/api/jobs", sessionCookie);

        assertThat(response.statusCode()).isEqualTo(200);
        final TypeList jobs = response.bodyAsMap().asList("jobs");
        assertThat(jobs).hasSize(2);
        final LinkedTypeMap firstJob = new LinkedTypeMap((Map<?, ?>) jobs.get(0));
        assertThat(firstJob.asString("projectSlug")).isEqualTo("neighborhood-repair-library");
        assertThat(firstJob.asString("projectTitle")).isEqualTo("Neighborhood Repair Library");
        assertThat(firstJob.asString("roleTitle")).isEqualTo("Tool Librarian");
        assertThat(firstJob.asString("roleCommitment")).contains("catalog tidy");
        assertThat(firstJob.asLong("roleId")).isPositive();
        assertThat(firstJob.keySet()).containsExactlyInAnyOrder("id", "roleId", "projectSlug", "projectTitle", "roleTitle", "roleCommitment");
        assertThat(jobs.stream().map(job -> new LinkedTypeMap((Map<?, ?>) job).asString("roleTitle")))
            .containsExactly("Tool Librarian", "Workshop Host");
    }

    @Test
    void sendsApplicationEmailToProjectOwner() {
        nano = newTestNano();
        final String sessionCookie = seedSession("applicant.jobs@example.test", "Applicant Builder", true);
        final long roleId = seedProjectWithRoles(
            "email-application-project",
            "Email Application Project",
            "active",
            List.of(
                new RoleSeed("Founder", "I lead this project.", true, false, 0),
                new RoleSeed("Community Coordinator", "Coordinate members and keep the work moving.", false, true, 1)
            )
        ).get(1);

        final HttpObject response = sendJson("/api/jobs/applications", Map.of(
            "roleId", roleId,
            "fit", "I have coordinated similar community work and can help members stay aligned.",
            "availability", "Two evenings per week."
        ), sessionCookie);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.bodyAsMap().asBoolean("sent")).isTrue();
        assertThat(emailSender.application).isNotNull();
        assertThat(emailSender.application.recipientEmail()).isEqualTo("email-application-project@example.test");
        assertThat(emailSender.application.projectTitle()).isEqualTo("Email Application Project");
        assertThat(emailSender.application.roleTitle()).isEqualTo("Community Coordinator");
        assertThat(emailSender.application.applicantName()).isEqualTo("Applicant Builder");
        assertThat(emailSender.application.applicantEmail()).isEqualTo("applicant.jobs@example.test");
        assertThat(emailSender.application.fit()).contains("community work");
        assertThat(emailSender.application.availability()).contains("evenings");
        assertThat(countApplications(roleId)).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateApplicationsForSameUserAndRole() {
        nano = newTestNano();
        final String sessionCookie = seedSession("duplicate.jobs@example.test", "Duplicate Applicant", true);
        final long roleId = seedProjectWithRoles(
            "duplicate-application-project",
            "Duplicate Application Project",
            "active",
            List.of(
                new RoleSeed("Founder", "I lead this project.", true, false, 0),
                new RoleSeed("Operations Lead", "Keep the team coordinated.", false, true, 1)
            )
        ).get(1);

        final Map<String, Object> payload = Map.of(
            "roleId", roleId,
            "fit", "I have enough operations experience to help this role work well.",
            "availability", ""
        );
        final HttpObject firstResponse = sendJson("/api/jobs/applications", payload, sessionCookie);
        final HttpObject duplicateResponse = sendJson("/api/jobs/applications", payload, sessionCookie);

        assertThat(firstResponse.statusCode()).isEqualTo(200);
        assertThat(duplicateResponse.statusCode()).isEqualTo(409);
        assertThat(duplicateResponse.bodyAsMap().asString("code")).isEqualTo(JobsUtil.JOB_APPLICATION_DUPLICATE_CODE);
        assertThat(emailSender.applicationCount).isEqualTo(1);
        assertThat(countApplications(roleId)).isEqualTo(1);
    }

    @Test
    void removesApplicationRecordWhenEmailSendFails() {
        nano = newTestNano();
        final String sessionCookie = seedSession("send.fail.jobs@example.test", "Send Fail Applicant", true);
        final long roleId = seedProjectWithRoles(
            "send-fail-project",
            "Send Fail Project",
            "active",
            List.of(
                new RoleSeed("Founder", "I lead this project.", true, false, 0),
                new RoleSeed("Delivery Lead", "Keep delivery work coordinated.", false, true, 1)
            )
        ).get(1);
        emailSender.failApplications = true;

        final HttpObject response = sendJson("/api/jobs/applications", Map.of(
            "roleId", roleId,
            "fit", "I have enough delivery coordination experience to help this role work.",
            "availability", ""
        ), sessionCookie);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.bodyAsMap().asString("code")).isEqualTo(JobsUtil.JOB_APPLICATION_SEND_FAILED_CODE);
        assertThat(countApplications(roleId)).isZero();
    }

    @Test
    void rejectsApplicationsForDeletedProjectOwners() {
        nano = newTestNano();
        final String sessionCookie = seedSession("deleted.owner.apply@example.test", "Deleted Owner Applicant", true);
        final long roleId = seedProjectWithRoles(
            "deleted-owner-apply-project",
            "Deleted Owner Apply Project",
            "active",
            List.of(
                new RoleSeed("Founder", "I started this project.", true, false, 0),
                new RoleSeed("Unavailable Role", "This role should not accept applications.", false, true, 1)
            )
        ).get(1);
        markUserDeleted("deleted-owner-apply-project@example.test");

        final HttpObject response = sendJson("/api/jobs/applications", Map.of(
            "roleId", roleId,
            "fit", "I have enough relevant experience to make this application valid.",
            "availability", ""
        ), sessionCookie);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.bodyAsMap().asString("code")).isEqualTo(JobsUtil.JOB_APPLICATION_NOT_FOUND_CODE);
        assertThat(emailSender.applicationCount).isZero();
        assertThat(countApplications(roleId)).isZero();
    }

    @Test
    void jobsRequireVerifiedMemberAccess() {
        nano = newTestNano();
        final String unverifiedCookie = seedSession("unverified.jobs@example.test", "Unverified Jobs", false);

        final HttpObject anonymousResponse = sendGet("/api/jobs", null);
        assertThat(anonymousResponse.statusCode()).isEqualTo(401);
        assertThat(anonymousResponse.bodyAsMap().asString("code")).isEqualTo(JobsUtil.JOBS_AUTH_REQUIRED_CODE);

        final HttpObject unverifiedResponse = sendGet("/api/jobs", unverifiedCookie);
        assertThat(unverifiedResponse.statusCode()).isEqualTo(403);
        assertThat(unverifiedResponse.bodyAsMap().asString("code")).isEqualTo(JobsUtil.JOBS_EMAIL_UNVERIFIED_CODE);
    }

    private Nano newTestNano() {
        final PostgresTestDatabase.DatabaseConfig databaseConfig = PostgresTestDatabase.createDatabase("jobs");
        TestDatabaseMigrations.migrate(databaseConfig.jdbcUrl(), databaseConfig.jdbcUser(), databaseConfig.jdbcPassword());
        databaseRuntime = new DatabaseRuntime(
            databaseConfig.jdbcUrl(),
            databaseConfig.jdbcUser(),
            databaseConfig.jdbcPassword(),
            "mitbauen-test-jobs"
        );
        emailSender = new CapturingEmailSender();
        return new Nano(
            Map.of(
                HttpServer.CONFIG_SERVICE_HTTP_PORT, 0
            ),
            new HttpServer(),
            new HttpClient(),
            new JobsService(databaseRuntime, emailSender)
        );
    }

    private HttpObject sendGet(final String path, final String sessionCookie) {
        final HttpObject request = new HttpObject().path(baseUrl(path));
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(JobsApiTest.class));
    }

    private HttpObject sendJson(final String path, final Map<String, Object> body, final String sessionCookie) {
        final HttpObject request = new HttpObject()
            .path(baseUrl(path))
            .methodType("POST")
            .contentType("application/json")
            .body(body);
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(JobsApiTest.class));
    }

    private String baseUrl(final String path) {
        return "http://localhost:" + nano.service(HttpServer.class).port() + path;
    }

    private String seedSession(final String email, final String displayName, final boolean verified) {
        final String token = "sess_test_" + email.replaceAll("[^a-z0-9]", "_");
        try (var connection = databaseRuntime.dataSource().getConnection();
             var userStatement = connection.prepareStatement("""
                 insert into users (display_name, email, bio, is_email_public, public_id, email_verified_at)
                 values (?, ?, '', false, ?, ?)
                 returning id
                 """);
             var sessionStatement = connection.prepareStatement("""
                 insert into sessions (user_id, token_hash, expires_at)
                 values (?, ?, ?)
                 """)) {
            userStatement.setString(1, displayName);
            userStatement.setString(2, email);
            userStatement.setString(3, "usr_" + email.replaceAll("[^a-z0-9]", "_"));
            userStatement.setTimestamp(4, verified ? Timestamp.from(Instant.now()) : null);
            final long userId;
            try (var resultSet = userStatement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                userId = resultSet.getLong("id");
            }
            sessionStatement.setLong(1, userId);
            sessionStatement.setString(2, AuthUtil.hashToken(token));
            sessionStatement.setTimestamp(3, Timestamp.from(Instant.now().plus(AuthUtil.SESSION_TTL)));
            sessionStatement.executeUpdate();
            return token;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to seed session for " + email, exception);
        }
    }

    private void markUserDeleted(final String email) {
        try (var connection = databaseRuntime.dataSource().getConnection();
             var statement = connection.prepareStatement("""
                 update users
                 set is_deleted = true, email_verified_at = null
                 where email = ?
                 """)) {
            statement.setString(1, email);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to mark user deleted " + email, exception);
        }
    }

    private long countApplications(final long roleId) {
        try (var connection = databaseRuntime.dataSource().getConnection();
             var statement = connection.prepareStatement("""
                 select count(*)
                 from job_applications
                 where role_id = ?
                 """)) {
            statement.setLong(1, roleId);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to count applications for role " + roleId, exception);
        }
    }

    private List<Long> seedProjectWithRoles(final String slug, final String title, final String status, final List<RoleSeed> roles) {
        try (var connection = databaseRuntime.dataSource().getConnection();
             var userStatement = connection.prepareStatement("""
                 insert into users (display_name, email, bio, is_email_public, public_id, email_verified_at)
                 values (?, ?, '', false, ?, current_timestamp)
                 returning id
                 """);
             var projectStatement = connection.prepareStatement("""
                 insert into projects (owner_user_id, slug, title, description_en, status, created_at, updated_at)
                 values (?, ?, ?, ?, ?, ?, ?)
                 returning id
                 """);
             var roleStatement = connection.prepareStatement("""
                 insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
                 values (?, ?, ?, ?, ?, ?)
                 returning id
                 """)) {
            userStatement.setString(1, title + " Owner");
            userStatement.setString(2, slug + "@example.test");
            userStatement.setString(3, "usr_" + slug.replace('-', '_'));
            final long ownerId;
            try (var resultSet = userStatement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                ownerId = resultSet.getLong("id");
            }

            final Timestamp now = Timestamp.from(Instant.now());
            projectStatement.setLong(1, ownerId);
            projectStatement.setString(2, slug);
            projectStatement.setString(3, title);
            projectStatement.setString(4, "A focused jobs fixture with a long enough description for the project schema.");
            projectStatement.setString(5, status);
            projectStatement.setTimestamp(6, now);
            projectStatement.setTimestamp(7, now);
            final long projectId;
            try (var resultSet = projectStatement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                projectId = resultSet.getLong("id");
            }

            final java.util.ArrayList<Long> roleIds = new java.util.ArrayList<>();
            for (RoleSeed role : roles) {
                roleStatement.setLong(1, projectId);
                roleStatement.setString(2, role.title());
                roleStatement.setString(3, role.commitment());
                roleStatement.setBoolean(4, role.founder());
                roleStatement.setBoolean(5, role.open());
                roleStatement.setInt(6, role.sortOrder());
                try (var resultSet = roleStatement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    roleIds.add(resultSet.getLong("id"));
                }
            }
            return roleIds;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to seed project " + slug, exception);
        }
    }

    private record RoleSeed(String title, String commitment, boolean founder, boolean open, int sortOrder) {
    }

    private static class CapturingEmailSender implements TransactionalEmailSender {
        private Application application;
        private int applicationCount;
        private boolean failApplications;

        @Override
        public void sendVerificationEmail(final String recipientEmail, final String recipientName, final String verificationUrl) {
        }

        @Override
        public void sendRoleApplicationEmail(
            final String recipientEmail,
            final String recipientName,
            final String projectTitle,
            final String roleTitle,
            final String applicantName,
            final String applicantEmail,
            final String fit,
            final String availability
        ) {
            if (failApplications) {
                throw new IllegalStateException("Email failed");
            }
            applicationCount++;
            application = new Application(recipientEmail, recipientName, projectTitle, roleTitle, applicantName, applicantEmail, fit, availability);
        }
    }

    private record Application(
        String recipientEmail,
        String recipientName,
        String projectTitle,
        String roleTitle,
        String applicantName,
        String applicantEmail,
        String fit,
        String availability
    ) {
    }
}
