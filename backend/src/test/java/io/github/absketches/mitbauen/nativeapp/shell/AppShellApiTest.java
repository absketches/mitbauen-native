package io.github.absketches.mitbauen.nativeapp.shell;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.services.http.HttpClient;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppShellApiTest {

    private Nano nano;

    @AfterEach
    void tearDown() {
        if (nano != null) {
            nano.stop(AppShellApiTest.class).waitForStop();
            nano = null;
        }
    }

    @Test
    void returnsServiceUnavailableForSpaShellRoutesWhenFrontendBundleIsMissing() {
        nano = new Nano(
            Map.of(HttpServer.CONFIG_SERVICE_HTTP_PORT, 0),
            new HttpServer(),
            new HttpClient(),
            new AppShellService()
        );

        final HttpObject rootResponse = new HttpObject()
            .path(baseUrl("/"))
            .send(nano.context(AppShellApiTest.class));

        final HttpObject nestedRouteResponse = new HttpObject()
            .path(baseUrl("/projects/new"))
            .send(nano.context(AppShellApiTest.class));

        assertShellRouteResponse(rootResponse);
        assertThat(rootResponse.header("cache-control")).isEqualTo("no-cache");

        assertShellRouteResponse(nestedRouteResponse);
        assertThat(nestedRouteResponse.header("cache-control")).isEqualTo("no-cache");
    }

    @Test
    void handlesOptionsMethodAndRejectsUnsupportedMethodsForShellRoutes() {
        nano = new Nano(
            Map.of(HttpServer.CONFIG_SERVICE_HTTP_PORT, 0),
            new HttpServer(),
            new HttpClient(),
            new AppShellService()
        );

        final HttpObject optionsResponse = new HttpObject()
            .path(baseUrl("/"))
            .methodType("OPTIONS")
            .send(nano.context(AppShellApiTest.class));

        final HttpObject postResponse = new HttpObject()
            .path(baseUrl("/"))
            .methodType("POST")
            .contentType("application/json")
            .body(Map.of("ignored", true))
            .send(nano.context(AppShellApiTest.class));

        assertThat(optionsResponse.statusCode()).isEqualTo(200);
        assertThat(optionsResponse.body()).isEmpty();

        assertThat(postResponse.statusCode()).isEqualTo(405);
        assertThat(postResponse.bodyAsMap().asString("error")).isEqualTo("Method Not Allowed");
        assertThat(postResponse.bodyAsMap().asString("path")).isEqualTo("/");
    }

    @Test
    void leavesApiAndMissingAssetPathsToOtherHandlers() {
        nano = new Nano(
            Map.of(HttpServer.CONFIG_SERVICE_HTTP_PORT, 0),
            new HttpServer(),
            new HttpClient(),
            new AppShellService()
        );

        final HttpObject apiResponse = new HttpObject()
            .path(baseUrl("/api/projects"))
            .send(nano.context(AppShellApiTest.class));

        final HttpObject missingAssetResponse = new HttpObject()
            .path(baseUrl("/missing-asset.js"))
            .send(nano.context(AppShellApiTest.class));

        assertThat(apiResponse.statusCode()).isEqualTo(404);
        assertThat(missingAssetResponse.statusCode()).isEqualTo(404);
    }

    private String baseUrl(final String path) {
        return "http://localhost:" + nano.service(HttpServer.class).port() + path;
    }

    private void assertShellRouteResponse(final HttpObject response) {
        assertThat(response.statusCode()).isIn(200, 503);
        if (response.statusCode() == 200) {
            assertThat(response.header("content-type")).contains("text/html");
            assertThat(response.bodyAsString()).isNotBlank();
        } else {
            assertThat(response.header("content-type")).contains("text/plain");
            assertThat(response.bodyAsString()).contains("Frontend bundle is missing");
        }
    }
}
