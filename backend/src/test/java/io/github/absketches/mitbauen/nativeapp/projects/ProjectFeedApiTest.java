package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseService;
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

class ProjectFeedApiTest {

    private Nano nano;

    @AfterEach
    void tearDown() {
        if (nano != null) {
            nano.stop(ProjectFeedApiTest.class).waitForStop();
        }
    }

    @Test
    void listsSeededProjectsInFeedOrder() {
        final String jdbcUrl = "jdbc:h2:mem:mitbauen_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        TestDatabaseMigrations.migrate(jdbcUrl, "sa", "");
        final DatabaseRuntime databaseRuntime = new DatabaseRuntime("mitbauen-test-feed");
        nano = new Nano(
            Map.of(
                HttpServer.CONFIG_SERVICE_HTTP_PORT, 0,
                DatabaseService.CONFIG_JDBC_DATABASE_URL, jdbcUrl,
                DatabaseService.CONFIG_JDBC_DATABASE_USER, "sa",
                DatabaseService.CONFIG_JDBC_DATABASE_PASSWORD, ""
            ),
            new HttpServer(),
            new HttpClient(),
            new DatabaseService(databaseRuntime),
            new ProjectFeedService(databaseRuntime)
        );

        final HttpObject response = new HttpObject()
            .path("http://localhost:" + nano.service(HttpServer.class).port() + "/api/projects")
            .send(nano.context(ProjectFeedApiTest.class));

        assertThat(response.statusCode()).isEqualTo(200);
        final LinkedTypeMap body = response.bodyAsMap();
        final TypeList projects = body.asList("projects");
        assertThat(projects).hasSize(4);

        final List<String> projectTitles = projects.stream()
            .map(project -> new LinkedTypeMap((Map<?, ?>) project).asString("title"))
            .toList();
        assertThat(projectTitles).containsExactly(
            "Solar For Neighbors",
            "Neighborhood Tool Library",
            "Campus Climate Hub",
            "Community Repair Bus"
        );

        final LinkedTypeMap firstProject = new LinkedTypeMap((Map<?, ?>) projects.get(0));
        assertThat(firstProject.asString("slug")).isEqualTo("solar-for-neighbors");
        assertThat(firstProject.asMap("founder").asString("role")).isEqualTo("Founder + Product");

        final TypeList firstProjectRoles = firstProject.asList("openRoles");
        assertThat(firstProjectRoles.stream()
            .map(role -> new LinkedTypeMap((Map<?, ?>) role).asString("title"))
            .toList()).containsExactly("Android Engineer", "Community Researcher");
    }
}
