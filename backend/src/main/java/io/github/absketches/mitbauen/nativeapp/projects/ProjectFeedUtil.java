package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import io.github.absketches.mitbauen.nativeapp.http.ResponseUtil;
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
    public static final int DESCRIPTION_MAX_LENGTH = 3000;
    public static final int FOUNDER_ROLE_MIN_LENGTH = 3;
    public static final int FOUNDER_ROLE_MAX_LENGTH = 120;
    public static final int FOUNDER_COMMITMENT_MIN_LENGTH = 5;
    public static final int FOUNDER_COMMITMENT_MAX_LENGTH = 500;
    public static final int OPEN_ROLE_TITLE_MIN_LENGTH = 3;
    public static final int OPEN_ROLE_TITLE_MAX_LENGTH = 120;
    public static final int OPEN_ROLE_COMMITMENT_MIN_LENGTH = 3;
    public static final int OPEN_ROLE_COMMITMENT_MAX_LENGTH = 500;
    public static final int OPEN_ROLES_MIN_COUNT = 1;
    public static final int OPEN_ROLES_MAX_COUNT = 6;
    public static final String PROJECT_NOT_FOUND_CODE = "PROJECT_NOT_FOUND";
    public static final String PROJECT_VIEW_AUTH_REQUIRED_CODE = "PROJECT_VIEW_AUTH_REQUIRED";
    public static final String PROJECT_CREATE_AUTH_REQUIRED_CODE = "PROJECT_CREATE_AUTH_REQUIRED";
    public static final String PROJECT_CREATE_EMAIL_UNVERIFIED_CODE = "PROJECT_CREATE_EMAIL_UNVERIFIED";
    public static final String PROJECT_EDIT_AUTH_REQUIRED_CODE = "PROJECT_EDIT_AUTH_REQUIRED";
    public static final String PROJECT_EDIT_EMAIL_UNVERIFIED_CODE = "PROJECT_EDIT_EMAIL_UNVERIFIED";
    public static final String PROJECT_EDIT_OWNER_REQUIRED_CODE = "PROJECT_EDIT_OWNER_REQUIRED";
    public static final String PROJECT_DELETE_AUTH_REQUIRED_CODE = "PROJECT_DELETE_AUTH_REQUIRED";
    public static final String PROJECT_DELETE_EMAIL_UNVERIFIED_CODE = "PROJECT_DELETE_EMAIL_UNVERIFIED";
    public static final String PROJECT_DELETE_OWNER_REQUIRED_CODE = "PROJECT_DELETE_OWNER_REQUIRED";
    public static final String PROJECT_TITLE_INVALID_CODE = "PROJECT_TITLE_INVALID";
    public static final String PROJECT_DESCRIPTION_INVALID_CODE = "PROJECT_DESCRIPTION_INVALID";
    public static final String PROJECT_FOUNDER_ROLE_INVALID_CODE = "PROJECT_FOUNDER_ROLE_INVALID";
    public static final String PROJECT_FOUNDER_COMMITMENT_INVALID_CODE = "PROJECT_FOUNDER_COMMITMENT_INVALID";
    public static final String PROJECT_OPEN_ROLES_MIN_CODE = "PROJECT_OPEN_ROLES_MIN";
    public static final String PROJECT_OPEN_ROLES_MAX_CODE = "PROJECT_OPEN_ROLES_MAX";
    public static final String PROJECT_OPEN_ROLE_TITLE_INVALID_CODE = "PROJECT_OPEN_ROLE_TITLE_INVALID";
    public static final String PROJECT_OPEN_ROLE_COMMITMENT_INVALID_CODE = "PROJECT_OPEN_ROLE_COMMITMENT_INVALID";
    public static final String METHOD_NOT_ALLOWED_CODE = "METHOD_NOT_ALLOWED";

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
        final String path = request.uri().getPath();
        if (path == null) {
            return new NoMatch();
        }
        if (path.equals(basePath)) {
            return new ProjectFeedRoute();
        }
        final String detailPrefix = basePath + "/";
        if (path.startsWith(detailPrefix)) {
            final String slug = path.substring(detailPrefix.length());
            if (!slug.isEmpty() && !slug.contains("/")) {
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
            return Optional.of(PROJECT_TITLE_INVALID_CODE);
        }
        if (outside(input.description(), DESCRIPTION_MIN_LENGTH, DESCRIPTION_MAX_LENGTH)) {
            return Optional.of(PROJECT_DESCRIPTION_INVALID_CODE);
        }
        if (outside(input.founderRole(), FOUNDER_ROLE_MIN_LENGTH, FOUNDER_ROLE_MAX_LENGTH)) {
            return Optional.of(PROJECT_FOUNDER_ROLE_INVALID_CODE);
        }
        if (outside(input.founderCommitment(), FOUNDER_COMMITMENT_MIN_LENGTH, FOUNDER_COMMITMENT_MAX_LENGTH)) {
            return Optional.of(PROJECT_FOUNDER_COMMITMENT_INVALID_CODE);
        }
        if (input.openRoles().size() < OPEN_ROLES_MIN_COUNT) {
            return Optional.of(PROJECT_OPEN_ROLES_MIN_CODE);
        }
        if (input.openRoles().size() > OPEN_ROLES_MAX_COUNT) {
            return Optional.of(PROJECT_OPEN_ROLES_MAX_CODE);
        }
        if (input.openRoles().stream().anyMatch(role -> outside(role.title(), OPEN_ROLE_TITLE_MIN_LENGTH, OPEN_ROLE_TITLE_MAX_LENGTH))) {
            return Optional.of(PROJECT_OPEN_ROLE_TITLE_INVALID_CODE);
        }
        if (input.openRoles().stream().anyMatch(role -> outside(role.commitment(), OPEN_ROLE_COMMITMENT_MIN_LENGTH, OPEN_ROLE_COMMITMENT_MAX_LENGTH))) {
            return Optional.of(PROJECT_OPEN_ROLE_COMMITMENT_INVALID_CODE);
        }
        return Optional.empty();
    }

    public static void respondProjects(final Event<HttpObject, HttpObject> event, final List<ProjectCard> projects) {
        ResponseUtil.respondOk(event, Map.of("projects", projects.stream().map(ProjectFeedUtil::projectToMap).toList()));
    }

    public static void respondProjectDetails(final Event<HttpObject, HttpObject> event, final ProjectDetails project, final boolean canManage) {
        ResponseUtil.respondOk(event, Map.of("project", projectDetailsToMap(project, canManage)));
    }

    public static void respondProjectSaved(final Event<HttpObject, HttpObject> event, final String slug, final int statusCode) {
        ResponseUtil.respondJson(event, statusCode, Map.of("slug", slug));
    }

    public static void respondDeleted(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondEmpty(event, 204);
    }

    public static void respondBadRequest(final Event<HttpObject, HttpObject> event, final String code) {
        ResponseUtil.respondBadRequest(event, code);
    }

    public static void respondUnauthorized(final Event<HttpObject, HttpObject> event, final String code) {
        ResponseUtil.respondUnauthorized(event, code);
    }

    public static void respondForbidden(final Event<HttpObject, HttpObject> event, final String code) {
        ResponseUtil.respondForbidden(event, code);
    }

    public static void respondNotFound(final Event<HttpObject, HttpObject> event, final String code) {
        ResponseUtil.respondNotFound(event, code);
    }

    public static void respondOptions(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondOptions(event);
    }

    public static void respondMethodNotAllowed(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondMethodNotAllowed(event, METHOD_NOT_ALLOWED_CODE);
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

    private static Map<String, Object> projectDetailsToMap(final ProjectDetails project, final boolean canManage) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", project.id());
        payload.put("canManage", canManage);
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
            "publicId", founder.publicId(),
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
}
