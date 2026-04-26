package io.github.absketches.mitbauen.nativeapp.projects;

import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.List;
import java.util.Map;

public class ProjectFeedUtil {

    public sealed interface RoutesMatch permits ProjectFeedRoute, NoMatch {
    }

    public record ProjectFeedRoute() implements RoutesMatch {
    }

    public record NoMatch() implements RoutesMatch {
    }

    private ProjectFeedUtil() {
    }

    public static RoutesMatch match(final HttpObject request, final String basePath) {
        if (request.pathMatch(basePath)) {
            return new ProjectFeedRoute();
        }
        return new NoMatch();
    }

    public static void respondProjects(final Event<HttpObject, HttpObject> event, final List<ProjectCard> projects) {
        event.payload().createCorsResponse()
            .statusCode(200)
            .body(Map.of("projects", projects.stream().map(ProjectFeedUtil::projectToMap).toList()))
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

    private static Map<String, Object> projectToMap(final ProjectCard project) {
        return Map.of(
            "id", project.id(),
            "slug", project.slug(),
            "title", project.title(),
            "summary", project.summary(),
            "status", project.status(),
            "founder", founderToMap(project.founder()),
            "openRoles", project.openRoles().stream().map(ProjectFeedUtil::openRoleToMap).toList(),
            "createdAt", project.createdAt().toString()
        );
    }

    private static Map<String, Object> founderToMap(final FounderInfo founder) {
        return Map.of(
            "name", founder.name(),
            "role", founder.role(),
            "commitment", founder.commitment()
        );
    }

    private static Map<String, Object> openRoleToMap(final OpenRole role) {
        return Map.of(
            "title", role.title(),
            "commitment", role.commitment()
        );
    }
}
