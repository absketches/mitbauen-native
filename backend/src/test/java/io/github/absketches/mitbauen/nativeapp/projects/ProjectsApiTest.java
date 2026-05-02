package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import io.github.absketches.mitbauen.nativeapp.auth.AuthService;
import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.TestDatabaseMigrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectsApiTest {

    private static final String OPEN_INVITE = "test-open-invite";
    private static final String PRIMARY_PASSWORD = "SuperSafe1";

    private Nano nano;
    private DatabaseRuntime databaseRuntime;

    @AfterEach
    void tearDown() {
        nano.stop(ProjectsApiTest.class).waitForStop();
        if (databaseRuntime != null) {
            databaseRuntime.stop();
            databaseRuntime = null;
        }
    }

    @Test
    void createsProjectLoadsDetailsAndShowsItFirstInFeed() {
        nano = newTestNano();
        final String sessionCookie = registerAndReturnSessionCookie("owner.one@example.test", "Avery Builder");

        final HttpObject createResponse = sendJson("/api/projects", "POST", Map.of(
            "title", "Circular Kitchen Atlas",
            "description", "A living guide for neighborhood kitchens that want to map surplus food, shared prep capacity, and the fastest path from extra ingredients to community meals.",
            "founderRole", "Founder + Community Ops",
            "founderCommitment", "I am running pilot dinners every week, documenting learnings, and handling the volunteer operations myself.",
            "openRoles", List.of(
                Map.of("title", "Frontend Engineer", "commitment", "Build the first contributor-facing workflows."),
                Map.of("title", "Research Partner", "commitment", "Interview hosts and turn patterns into playbooks.")
            )
        ), sessionCookie);

        assertThat(createResponse.statusCode()).isEqualTo(201);
        final String slug = createResponse.bodyAsMap().asString("slug");
        assertThat(slug).isEqualTo("circular-kitchen-atlas");

        final HttpObject detailResponse = sendGet("/api/projects/" + slug, null);
        assertThat(detailResponse.statusCode()).isEqualTo(200);
        final LinkedTypeMap project = detailResponse.bodyAsMap().asMap("project");
        assertThat(project.asString("title")).isEqualTo("Circular Kitchen Atlas");
        assertThat(project.asString("description")).contains("surplus food");
        assertThat(project.asMap("founder").asString("role")).isEqualTo("Founder + Community Ops");
        assertThat(project.asMap("founder").asString("commitment")).contains("pilot dinners every week");

        final TypeList roles = project.asList("openRoles");
        assertThat(roles).hasSize(2);
        assertThat(roles.stream()
            .map(role -> new LinkedTypeMap((Map<?, ?>) role).asString("title"))
            .toList())
            .containsExactly("Frontend Engineer", "Research Partner");

        final HttpObject feedResponse = sendGet("/api/projects", null);
        assertThat(feedResponse.statusCode()).isEqualTo(200);
        final TypeList feedProjects = feedResponse.bodyAsMap().asList("projects");
        final LinkedTypeMap firstProject = new LinkedTypeMap((Map<?, ?>) feedProjects.get(0));
        assertThat(firstProject.asString("slug")).isEqualTo(slug);
    }

    @Test
    void rejectsAnonymousProjectCreation() {
        nano = newTestNano();

        final HttpObject response = sendJson("/api/projects", "POST", Map.of(
            "title", "Hidden Makerspace Calendar",
            "description", "A shared calendar and intake flow for community workshop nights so small makerspaces can coordinate volunteers and avoid duplicated prep work.",
            "founderRole", "Founder + Organizer",
            "founderCommitment", "I am already hosting the sessions, coordinating signups, and setting up the space each week.",
            "openRoles", List.of(Map.of("title", "Designer", "commitment", "Shape the first scheduling flow."))
        ), null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.bodyAsMap().asString("error")).isEqualTo("You must be signed in to create a project.");
    }

    @Test
    void rejectsProjectWithoutOpenRoles() {
        nano = newTestNano();
        final String sessionCookie = registerAndReturnSessionCookie("owner.two@example.test", "Nora Builder");

        final HttpObject response = sendJson("/api/projects", "POST", Map.of(
            "title", "Repair Story Archive",
            "description", "A simple library for documenting repair stories so volunteers can remember what failed, what worked, and what tools they needed last time.",
            "founderRole", "Founder + Archivist",
            "founderCommitment", "I am already gathering the stories, scanning notes, and interviewing the first volunteer repair teams.",
            "openRoles", List.of()
        ), sessionCookie);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.bodyAsMap().asString("error")).isEqualTo("Add at least one open role.");
    }

    @Test
    void onlyProjectOwnerCanEdit() {
        nano = newTestNano();
        final String ownerCookie = registerAndReturnSessionCookie("owner.three@example.test", "Mika Owner");
        final String intruderCookie = registerAndReturnSessionCookie("intruder@example.test", "Elliot Intruder");

        final String slug = sendJson("/api/projects", "POST", Map.of(
            "title", "Block Heat Map",
            "description", "A neighborhood heat resilience project that tracks shade, cooling access, and high-risk blocks so small mutual-aid teams can coordinate the right help faster.",
            "founderRole", "Founder + Coordinator",
            "founderCommitment", "I am already walking the routes, meeting residents, and coordinating the volunteer response plan each week.",
            "openRoles", List.of(Map.of("title", "Data Volunteer", "commitment", "Map the first round of block conditions."))
        ), ownerCookie).bodyAsMap().asString("slug");

        final HttpObject forbiddenEdit = sendJson("/api/projects/" + slug, "PUT", Map.of(
            "title", "Block Heat Map Revised",
            "description", "A revised description that should never be saved because a non-owner is attempting the edit through the public API.",
            "founderRole", "Founder + Coordinator",
            "founderCommitment", "Still coordinating everything.",
            "openRoles", List.of(Map.of("title", "Data Volunteer", "commitment", "Still mapping conditions."))
        ), intruderCookie);

        assertThat(forbiddenEdit.statusCode()).isEqualTo(403);
        assertThat(forbiddenEdit.bodyAsMap().asString("error")).isEqualTo("Only the project owner can edit this project.");

        final HttpObject ownerEdit = sendJson("/api/projects/" + slug, "PUT", Map.of(
            "title", "Block Heat Map",
            "description", "A neighborhood heat resilience project that tracks shade, cooling access, and high-risk blocks so small mutual-aid teams can coordinate the right help faster, with clearer volunteer handoffs.",
            "founderRole", "Founder + Heat Response Lead",
            "founderCommitment", "I am coordinating weekly walks, resident calls, and volunteer handoffs while piloting the first interventions myself.",
            "openRoles", List.of(
                Map.of("title", "Data Volunteer", "commitment", "Map the next round of block conditions."),
                Map.of("title", "Operations Support", "commitment", "Coordinate water, shade, and check-in logistics.")
            )
        ), ownerCookie);

        assertThat(ownerEdit.statusCode()).isEqualTo(200);
        assertThat(ownerEdit.bodyAsMap().asString("slug")).isEqualTo(slug);

        final LinkedTypeMap updatedProject = sendGet("/api/projects/" + slug, null).bodyAsMap().asMap("project");
        assertThat(updatedProject.asString("description")).contains("clearer volunteer handoffs");
        assertThat(updatedProject.asMap("founder").asString("role")).isEqualTo("Founder + Heat Response Lead");
        assertThat(updatedProject.asList("openRoles").stream()
            .map(role -> new LinkedTypeMap((Map<?, ?>) role).asString("title"))
            .toList())
            .containsExactly("Data Volunteer", "Operations Support");
    }

    @Test
    void onlyProjectOwnerCanDelete() {
        nano = newTestNano();
        final String ownerCookie = registerAndReturnSessionCookie("owner.delete@example.test", "Owner Delete");
        final String intruderCookie = registerAndReturnSessionCookie("intruder.delete@example.test", "Intruder Delete");

        final String slug = sendJson("/api/projects", "POST", Map.of(
            "title", "Community Repair Ledger",
            "description", "A shared ledger for volunteer repair collectives so each fix, failed attempt, and reused part can be tracked and learned from across neighborhoods.",
            "founderRole", "Founder + Repair Lead",
            "founderCommitment", "I am already organizing the repair nights, capturing the notes, and coordinating the volunteers every week.",
            "openRoles", List.of(Map.of("title", "Data Steward", "commitment", "Organize the repair records."))
        ), ownerCookie).bodyAsMap().asString("slug");

        final HttpObject forbiddenDelete = sendRequest("/api/projects/" + slug, "DELETE", null, intruderCookie);
        assertThat(forbiddenDelete.statusCode()).isEqualTo(403);
        assertThat(forbiddenDelete.bodyAsMap().asString("error")).isEqualTo("Only the project owner can delete this project.");

        final HttpObject ownerDelete = sendRequest("/api/projects/" + slug, "DELETE", null, ownerCookie);
        assertThat(ownerDelete.statusCode()).isEqualTo(204);

        final HttpObject missingDetail = sendGet("/api/projects/" + slug, null);
        assertThat(missingDetail.statusCode()).isEqualTo(404);

        final TypeList feedProjects = sendGet("/api/projects", null).bodyAsMap().asList("projects");
        assertThat(feedProjects.stream()
            .map(project -> new LinkedTypeMap((Map<?, ?>) project).asString("slug"))
            .toList())
            .doesNotContain(slug);
    }

    private Nano newTestNano() {
        final String jdbcUrl = "jdbc:h2:mem:mitbauen_projects_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        TestDatabaseMigrations.migrate(jdbcUrl, "sa", "");
        TestDatabaseMigrations.seedInvite(jdbcUrl, "sa", "", OPEN_INVITE);
        databaseRuntime = new DatabaseRuntime(jdbcUrl, "sa", "", "mitbauen-test-projects");
        return new Nano(
            Map.of(
                HttpServer.CONFIG_SERVICE_HTTP_PORT, 0
            ),
            new HttpServer(),
            new HttpClient(),
            new AuthService(databaseRuntime),
            new ProjectFeedService(databaseRuntime)
        );
    }

    private String registerAndReturnSessionCookie(final String email, final String displayName) {
        final HttpObject response = sendJson("/api/auth/register", "POST", Map.of(
            "inviteToken", OPEN_INVITE,
            "email", email,
            "displayName", displayName,
            "password", PRIMARY_PASSWORD
        ), null);
        assertThat(response.statusCode()).isEqualTo(201);
        return cookieValue(response, AuthUtil.AUTH_SESSION_COOKIE);
    }

    private HttpObject sendGet(final String path, final String sessionCookie) {
        final HttpObject request = new HttpObject().path(baseUrl(path));
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(ProjectsApiTest.class));
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
        return request.send(nano.context(ProjectsApiTest.class));
    }

    private HttpObject sendRequest(final String path, final String method, final Map<String, Object> body, final String sessionCookie) {
        final HttpObject request = new HttpObject()
            .path(baseUrl(path))
            .methodType(method);
        if (body != null) {
            request.contentType("application/json").body(body);
        }
        if (sessionCookie != null) {
            request.header("Cookie", AuthUtil.AUTH_SESSION_COOKIE + "=" + sessionCookie);
        }
        return request.send(nano.context(ProjectsApiTest.class));
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
