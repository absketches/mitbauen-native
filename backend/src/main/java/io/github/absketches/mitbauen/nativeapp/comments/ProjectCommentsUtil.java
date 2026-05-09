package io.github.absketches.mitbauen.nativeapp.comments;

import berlin.yuna.typemap.model.LinkedTypeMap;
import io.github.absketches.mitbauen.nativeapp.http.ResponseUtil;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProjectCommentsUtil {

    public static final String PROJECTS_BASE_PATH = "/api/projects";
    public static final int COMMENT_BODY_MAX_LENGTH = 1000;
    public static final String AUTH_REQUIRED_CODE = "PROJECT_COMMENTS_AUTH_REQUIRED";
    public static final String EMAIL_UNVERIFIED_CODE = "PROJECT_COMMENTS_EMAIL_UNVERIFIED";
    public static final String COMMENT_EMPTY_CODE = "PROJECT_COMMENT_EMPTY";
    public static final String COMMENT_TOO_LONG_CODE = "PROJECT_COMMENT_TOO_LONG";
    public static final String PROJECT_NOT_FOUND_CODE = "PROJECT_NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED_CODE = "METHOD_NOT_ALLOWED";

    public sealed interface RouteMatch permits ProjectCommentsRoute, ProjectCommentsReadRoute, NoMatch {
    }

    public record ProjectCommentsRoute(String slug) implements RouteMatch {
    }

    public record ProjectCommentsReadRoute(String slug) implements RouteMatch {
    }

    public record NoMatch() implements RouteMatch {
    }

    private ProjectCommentsUtil() {
    }

    public static RouteMatch match(final HttpObject request) {
        final String path = request.uri().getPath();
        if (path == null) {
            return new NoMatch();
        }
        final String projectPrefix = PROJECTS_BASE_PATH + "/";
        if (!path.startsWith(projectPrefix)) {
            return new NoMatch();
        }
        final String suffix = path.substring(projectPrefix.length());
        final String commentsSuffix = "/comments";
        final String readSuffix = "/comments/read";
        if (suffix.endsWith(readSuffix)) {
            final String slug = suffix.substring(0, suffix.length() - readSuffix.length());
            return validSlug(slug)
                .<RouteMatch>map(ProjectCommentsReadRoute::new)
                .orElseGet(NoMatch::new);
        }
        if (suffix.endsWith(commentsSuffix)) {
            final String slug = suffix.substring(0, suffix.length() - commentsSuffix.length());
            return validSlug(slug)
                .<RouteMatch>map(ProjectCommentsRoute::new)
                .orElseGet(NoMatch::new);
        }
        return new NoMatch();
    }

    public static String commentBodyFrom(final LinkedTypeMap body) {
        final String value = body.asString("body");
        return value == null ? "" : value.trim();
    }

    public static Optional<String> validateCommentBody(final String body) {
        if (body.isBlank()) {
            return Optional.of(COMMENT_EMPTY_CODE);
        }
        if (body.length() > COMMENT_BODY_MAX_LENGTH) {
            return Optional.of(COMMENT_TOO_LONG_CODE);
        }
        return Optional.empty();
    }

    public static void respondComments(final Event<HttpObject, HttpObject> event, final List<ProjectComment> comments) {
        ResponseUtil.respondOk(event, Map.of("comments", comments.stream().map(ProjectCommentsUtil::commentToMap).toList()));
    }

    public static void respondCommentCreated(final Event<HttpObject, HttpObject> event, final ProjectComment comment) {
        ResponseUtil.respondCreated(event, Map.of("comment", commentToMap(comment)));
    }

    public static void respondRead(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondOk(event, Map.of("read", true));
    }

    public static void respondBadRequest(final Event<HttpObject, HttpObject> event, final String code) {
        ResponseUtil.respondBadRequest(event, code);
    }

    public static void respondUnauthorized(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondUnauthorized(event, AUTH_REQUIRED_CODE);
    }

    public static void respondForbidden(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondForbidden(event, EMAIL_UNVERIFIED_CODE);
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

    private static Optional<String> validSlug(final String slug) {
        if (slug.isEmpty() || slug.contains("/")) {
            return Optional.empty();
        }
        return Optional.of(slug);
    }

    private static Map<String, Object> commentToMap(final ProjectComment comment) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", comment.id());
        payload.put("body", comment.body());
        payload.put("authorPublicId", comment.authorPublicId());
        payload.put("authorDisplayName", comment.authorDisplayName());
        payload.put("createdAt", comment.createdAt().toString());
        return payload;
    }
}
