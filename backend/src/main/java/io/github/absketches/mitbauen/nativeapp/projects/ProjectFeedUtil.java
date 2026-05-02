package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProjectFeedUtil {

    public static final int TITLE_MIN_LENGTH = 5;
    public static final int TITLE_MAX_LENGTH = 120;
    public static final int DESCRIPTION_MIN_LENGTH = 40;
    public static final int DESCRIPTION_MAX_LENGTH = 1024;
    public static final int FOUNDER_ROLE_MIN_LENGTH = 3;
    public static final int FOUNDER_ROLE_MAX_LENGTH = 80;
    public static final int FOUNDER_COMMITMENT_MIN_LENGTH = 10;
    public static final int FOUNDER_COMMITMENT_MAX_LENGTH = 280;
    public static final int OPEN_ROLE_TITLE_MIN_LENGTH = 3;
    public static final int OPEN_ROLE_TITLE_MAX_LENGTH = 80;
    public static final int OPEN_ROLE_COMMITMENT_MIN_LENGTH = 3;
    public static final int OPEN_ROLE_COMMITMENT_MAX_LENGTH = 80;
    public static final int OPEN_ROLES_MIN_COUNT = 1;
    public static final int OPEN_ROLES_MAX_COUNT = 6;

    public sealed interface RoutesMatch permits ProjectFeedRoute, ProjectDetailsRoute, NoMatch {
    }

    public record ProjectFeedRoute() implements RoutesMatch {
    }

    public record ProjectDetailsRoute(String slug) implements RoutesMatch {
    }

    public record NoMatch() implements RoutesMatch {
    }

    private ProjectFeedUtil() {
    }

    public static RoutesMatch match(final HttpObject request, final String basePath) {
        final String requestPath = normalizePath(request.uri().getPath());
        if (requestPath.equals(basePath)) {
            return new ProjectFeedRoute();
        }
        final String detailPrefix = basePath + "/";
        if (requestPath.startsWith(detailPrefix)) {
            final String slug = requestPath.substring(detailPrefix.length()).trim();
            if (!slug.isBlank() && !slug.contains("/")) {
                return new ProjectDetailsRoute(slug);
            }
        }
        return new NoMatch();
    }

    public static ProjectInput projectInputFrom(final LinkedTypeMap body) {
        final TypeList roles = body.asList("openRoles");
        return new ProjectInput(
            safeTrim(body.asString("title")),
            safeTrim(body.asString("description")),
            safeTrim(body.asString("founderRole")),
            safeTrim(body.asString("founderCommitment")),
            roles == null ? List.of() : roles.stream()
                .map(role -> role instanceof LinkedTypeMap linkedTypeMap ? linkedTypeMap : new LinkedTypeMap((Map<?, ?>) role))
                .map(role -> new OpenRole(
                    safeTrim(role.asString("title")),
                    safeTrim(role.asString("commitment"))
                ))
                .toList()
        );
    }

    public static Optional<String> validateProjectInput(final ProjectInput input) {
        if (outside(input.title(), TITLE_MIN_LENGTH, TITLE_MAX_LENGTH)) {
            return Optional.of("Project title must be between 5 and 120 characters.");
        }
        if (outside(input.description(), DESCRIPTION_MIN_LENGTH, DESCRIPTION_MAX_LENGTH)) {
            return Optional.of("Project description must be between 40 and 1024 characters.");
        }
        if (outside(input.founderRole(), FOUNDER_ROLE_MIN_LENGTH, FOUNDER_ROLE_MAX_LENGTH)) {
            return Optional.of("Founder role must be between 3 and 80 characters.");
        }
        if (outside(input.founderCommitment(), FOUNDER_COMMITMENT_MIN_LENGTH, FOUNDER_COMMITMENT_MAX_LENGTH)) {
            return Optional.of("Founder commitment must be between 10 and 280 characters.");
        }
        if (input.openRoles().size() < OPEN_ROLES_MIN_COUNT) {
            return Optional.of("Add at least one open role.");
        }
        if (input.openRoles().size() > OPEN_ROLES_MAX_COUNT) {
            return Optional.of("You can add up to 6 open roles.");
        }
        if (input.openRoles().stream().anyMatch(role -> outside(role.title(), OPEN_ROLE_TITLE_MIN_LENGTH, OPEN_ROLE_TITLE_MAX_LENGTH))) {
            return Optional.of("Each open role title must be between 3 and 80 characters.");
        }
        if (input.openRoles().stream().anyMatch(role -> outside(role.commitment(), OPEN_ROLE_COMMITMENT_MIN_LENGTH, OPEN_ROLE_COMMITMENT_MAX_LENGTH))) {
            return Optional.of("Each open role commitment must be between 3 and 80 characters.");
        }
        return Optional.empty();
    }

    public static void respondProjects(final Event<HttpObject, HttpObject> event, final List<ProjectCard> projects) {
        event.payload().createCorsResponse()
            .statusCode(200)
            .body(Map.of("projects", projects.stream().map(ProjectFeedUtil::projectToMap).toList()))
            .respond(event);
    }

    public static void respondProjectDetails(final Event<HttpObject, HttpObject> event, final ProjectDetails project) {
        event.payload().createCorsResponse()
            .statusCode(200)
            .body(Map.of("project", projectDetailsToMap(project)))
            .respond(event);
    }

    public static void respondProjectSaved(final Event<HttpObject, HttpObject> event, final String slug, final int statusCode) {
        event.payload().createCorsResponse()
            .statusCode(statusCode)
            .body(Map.of("slug", slug))
            .respond(event);
    }

    public static void respondDeleted(final Event<HttpObject, HttpObject> event) {
        event.payload().createCorsResponse()
            .statusCode(204)
            .respond(event);
    }

    public static void respondBadRequest(final Event<HttpObject, HttpObject> event, final String message) {
        event.payload().createCorsResponse()
            .statusCode(400)
            .body(Map.of("error", message))
            .respond(event);
    }

    public static void respondUnauthorized(final Event<HttpObject, HttpObject> event, final String message) {
        event.payload().createCorsResponse()
            .statusCode(401)
            .body(Map.of("error", message))
            .respond(event);
    }

    public static void respondForbidden(final Event<HttpObject, HttpObject> event, final String message) {
        event.payload().createCorsResponse()
            .statusCode(403)
            .body(Map.of("error", message))
            .respond(event);
    }

    public static void respondNotFound(final Event<HttpObject, HttpObject> event, final String message) {
        event.payload().createCorsResponse()
            .statusCode(404)
            .body(Map.of("error", message))
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

    private static Map<String, Object> projectToMap(final ProjectCard project) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", project.id());
        payload.put("slug", project.slug());
        payload.put("title", project.title());
        payload.put("description", project.description());
        payload.put("status", project.status());
        payload.put("founder", founderToMap(project.founder()));
        payload.put("openRoles", project.openRoles().stream().map(ProjectFeedUtil::openRoleToMap).toList());
        payload.put("createdAt", project.createdAt().toString());
        return payload;
    }

    private static Map<String, Object> projectDetailsToMap(final ProjectDetails project) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", project.id());
        payload.put("ownerUserId", project.ownerUserId());
        payload.put("slug", project.slug());
        payload.put("title", project.title());
        payload.put("description", project.description());
        payload.put("status", project.status());
        payload.put("founder", founderToMap(project.founder()));
        payload.put("openRoles", project.openRoles().stream().map(ProjectFeedUtil::openRoleToMap).toList());
        payload.put("createdAt", project.createdAt().toString());
        payload.put("updatedAt", project.updatedAt().toString());
        return payload;
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

    private static boolean outside(final String value, final int minimum, final int maximum) {
        return value == null || value.length() < minimum || value.length() > maximum;
    }

    private static String safeTrim(final String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizePath(final String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
