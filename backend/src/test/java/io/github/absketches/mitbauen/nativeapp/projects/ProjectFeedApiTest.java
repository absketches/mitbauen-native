package io.github.absketches.mitbauen.nativeapp.projects;

import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.db.PostgresTestDatabase;
import io.github.absketches.mitbauen.nativeapp.db.TestDatabaseMigrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectFeedApiTest {

    private Nano nano;
    private DatabaseRuntime databaseRuntime;

    @AfterEach
    void tearDown() {
        if (nano != null) {
            nano.stop(ProjectFeedApiTest.class).waitForStop();
            nano = null;
        }
        if (databaseRuntime != null) {
            databaseRuntime.stop();
            databaseRuntime = null;
        }
    }

    @Test
    void rejectsAnonymousProjectFeedAccess() {
        final PostgresTestDatabase.DatabaseConfig databaseConfig = PostgresTestDatabase.createDatabase("feed");
        TestDatabaseMigrations.migrate(databaseConfig.jdbcUrl(), databaseConfig.jdbcUser(), databaseConfig.jdbcPassword());
        databaseRuntime = new DatabaseRuntime(
            databaseConfig.jdbcUrl(),
            databaseConfig.jdbcUser(),
            databaseConfig.jdbcPassword(),
            "mitbauen-test-feed"
        );
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

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.bodyAsMap().asString("code")).isEqualTo(ProjectFeedUtil.PROJECT_VIEW_AUTH_REQUIRED_CODE);
    }
}
