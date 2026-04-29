package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
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

class ProjectFeedApiTest {

    private Nano nano;
    private DatabaseRuntime databaseRuntime;

    @AfterEach
    void tearDown() {
        if (nano != null) {
            nano.stop(ProjectFeedApiTest.class).waitForStop();
        }
        if (databaseRuntime != null) {
            databaseRuntime.stop();
            databaseRuntime = null;
        }
    }

    @Test
    void returnsAnEmptyFeedWhenNoProjectsExist() {
        final String jdbcUrl = "jdbc:h2:mem:mitbauen_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        TestDatabaseMigrations.migrate(jdbcUrl, "sa", "");
        databaseRuntime = new DatabaseRuntime(jdbcUrl, "sa", "", "mitbauen-test-feed");
        nano = new Nano(
            Map.of(
                HttpServer.CONFIG_SERVICE_HTTP_PORT, 0
            ),
            new HttpServer(),
            new HttpClient(),
            new ProjectFeedService(databaseRuntime)
        );

        final HttpObject response = new HttpObject()
            .path("http://localhost:" + nano.service(HttpServer.class).port() + "/api/projects")
            .send(nano.context(ProjectFeedApiTest.class));

        assertThat(response.statusCode()).isEqualTo(200);
        final LinkedTypeMap body = response.bodyAsMap();
        final TypeList projects = body.asList("projects");
        assertThat(projects).isEmpty();
    }
}
