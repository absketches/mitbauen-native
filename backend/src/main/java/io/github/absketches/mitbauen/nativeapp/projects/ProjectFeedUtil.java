package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import io.github.absketches.mitbauen.nativeapp.http.ResponseUtil;
import io.github.absketches.mitbauen.nativeapp.projects.links.ProjectLink;
import io.github.absketches.mitbauen.nativeapp.projects.media.ProjectImage;
import io.github.absketches.mitbauen.nativeapp.projects.media.ProjectImageContent;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public static final int PROJECT_IMAGES_MAX_COUNT = 5;
    public static final int PROJECT_IMAGE_MAX_BYTES = 2 * 1024 * 1024;
    public static final int PROJECT_IMAGE_ALT_TEXT_MAX_LENGTH = 160;
    public static final int PROJECT_LINKS_MAX_COUNT = 8;
    public static final int PROJECT_LINK_LABEL_MIN_LENGTH = 2;
    public static final int PROJECT_LINK_LABEL_MAX_LENGTH = 40;
    public static final int PROJECT_LINK_URL_MAX_LENGTH = 2048;
    public static final String PROJECT_NOT_FOUND_CODE = "PROJECT_NOT_FOUND";
    public static final String PROJECT_VIEW_AUTH_REQUIRED_CODE = "PROJECT_VIEW_AUTH_REQUIRED";
    public static final String PROJECT_VIEW_EMAIL_UNVERIFIED_CODE = "PROJECT_VIEW_EMAIL_UNVERIFIED";
    public static final String PROJECT_CREATE_AUTH_REQUIRED_CODE = "PROJECT_CREATE_AUTH_REQUIRED";
    public static final String PROJECT_CREATE_EMAIL_UNVERIFIED_CODE = "PROJECT_CREATE_EMAIL_UNVERIFIED";
    public static final String PROJECT_EDIT_AUTH_REQUIRED_CODE = "PROJECT_EDIT_AUTH_REQUIRED";
    public static final String PROJECT_EDIT_EMAIL_UNVERIFIED_CODE = "PROJECT_EDIT_EMAIL_UNVERIFIED";
    public static final String PROJECT_EDIT_OWNER_REQUIRED_CODE = "PROJECT_EDIT_OWNER_REQUIRED";
    public static final String PROJECT_DELETE_AUTH_REQUIRED_CODE = "PROJECT_DELETE_AUTH_REQUIRED";
    public static final String PROJECT_DELETE_EMAIL_UNVERIFIED_CODE = "PROJECT_DELETE_EMAIL_UNVERIFIED";
    public static final String PROJECT_DELETE_OWNER_REQUIRED_CODE = "PROJECT_DELETE_OWNER_REQUIRED";
    public static final String PROJECT_IMAGE_NOT_FOUND_CODE = "PROJECT_IMAGE_NOT_FOUND";
    public static final String PROJECT_IMAGE_AUTH_REQUIRED_CODE = "PROJECT_IMAGE_AUTH_REQUIRED";
    public static final String PROJECT_IMAGE_EMAIL_UNVERIFIED_CODE = "PROJECT_IMAGE_EMAIL_UNVERIFIED";
    public static final String PROJECT_IMAGE_OWNER_REQUIRED_CODE = "PROJECT_IMAGE_OWNER_REQUIRED";
    public static final String PROJECT_IMAGE_TYPE_INVALID_CODE = "PROJECT_IMAGE_TYPE_INVALID";
    public static final String PROJECT_IMAGE_TOO_LARGE_CODE = "PROJECT_IMAGE_TOO_LARGE";
    public static final String PROJECT_IMAGES_MAX_CODE = "PROJECT_IMAGES_MAX";
    public static final String PROJECT_IMAGE_ALT_TEXT_INVALID_CODE = "PROJECT_IMAGE_ALT_TEXT_INVALID";
    public static final String PROJECT_TITLE_INVALID_CODE = "PROJECT_TITLE_INVALID";
    public static final String PROJECT_DESCRIPTION_INVALID_CODE = "PROJECT_DESCRIPTION_INVALID";
    public static final String PROJECT_FOUNDER_ROLE_INVALID_CODE = "PROJECT_FOUNDER_ROLE_INVALID";
    public static final String PROJECT_FOUNDER_COMMITMENT_INVALID_CODE = "PROJECT_FOUNDER_COMMITMENT_INVALID";
    public static final String PROJECT_OPEN_ROLES_MIN_CODE = "PROJECT_OPEN_ROLES_MIN";
    public static final String PROJECT_OPEN_ROLES_MAX_CODE = "PROJECT_OPEN_ROLES_MAX";
    public static final String PROJECT_OPEN_ROLE_TITLE_INVALID_CODE = "PROJECT_OPEN_ROLE_TITLE_INVALID";
    public static final String PROJECT_OPEN_ROLE_COMMITMENT_INVALID_CODE = "PROJECT_OPEN_ROLE_COMMITMENT_INVALID";
    public static final String PROJECT_LINKS_MAX_CODE = "PROJECT_LINKS_MAX";
    public static final String PROJECT_LINK_LABEL_INVALID_CODE = "PROJECT_LINK_LABEL_INVALID";
    public static final String PROJECT_LINK_URL_INVALID_CODE = "PROJECT_LINK_URL_INVALID";
    public static final String PROJECT_PAYLOAD_INVALID_CODE = "PROJECT_PAYLOAD_INVALID";
    public static final String METHOD_NOT_ALLOWED_CODE = "METHOD_NOT_ALLOWED";

    public sealed interface RoutesMatch permits ProjectFeedRoute, ProjectDetailsRoute, ProjectImagesRoute, ProjectImageRoute, NoMatch {
    }

    public record ProjectFeedRoute() implements RoutesMatch {
    }

    public record ProjectDetailsRoute(String slug) implements RoutesMatch {
    }

    public record ProjectImagesRoute(String slug) implements RoutesMatch {
    }

    public record ProjectImageRoute(String slug, long imageId) implements RoutesMatch {
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
        if (!path.startsWith(basePath + "/")) {
            return new NoMatch();
        }

        return matchProjectPath(path.substring(basePath.length() + 1).split("/", -1));
    }

    private static RoutesMatch matchProjectPath(final String[] segments) {
        if (segments.length == 1 && validSlugSegment(segments[0])) {
            return new ProjectDetailsRoute(segments[0]);
        }
        if (segments.length == 2 && validSlugSegment(segments[0]) && "images".equals(segments[1])) {
            return new ProjectImagesRoute(segments[0]);
        }
        if (segments.length == 3 && validSlugSegment(segments[0]) && "images".equals(segments[1])) {
            return imageRoute(segments[0], segments[2]);
        }
        return new NoMatch();
    }

    private static RoutesMatch imageRoute(final String slug, final String imageIdSegment) {
        try {
            final long imageId = Long.parseLong(imageIdSegment);
            return imageId > 0 ? new ProjectImageRoute(slug, imageId) : new NoMatch();
        } catch (NumberFormatException exception) {
            return new NoMatch();
        }
    }

    private static boolean validSlugSegment(final String slug) {
        return slug != null && !slug.isBlank();
    }

    public static ProjectInput projectInputFrom(final LinkedTypeMap body) {
        final TypeList roles = body.asList("openRoles");
        final TypeList links = body.asList("links");
        return new ProjectInput(
            safeTrim(body.asString("title")),
            descriptionsFrom(body),
            safeTrim(body.asString("founderRole")),
            safeTrim(body.asString("founderCommitment")),
            roles == null ? List.of() : roles.stream()
                .map(ProjectFeedUtil::linkedTypeMapFromListItem)
                .map(role -> new OpenRole(
                    safeTrim(role.asString("title")),
                    safeTrim(role.asString("commitment"))
                ))
                .toList(),
            links == null ? List.of() : links.stream()
                .map(ProjectFeedUtil::linkedTypeMapFromListItem)
                .map(link -> new ProjectLink(
                    safeTrim(link.asString("label")),
                    safeTrim(link.asString("url"))
                ))
                .toList()
        );
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

    public static void respondProjectImageSaved(final Event<HttpObject, HttpObject> event, final String slug, final ProjectImage image) {
        ResponseUtil.respondCreated(event, Map.of("image", projectImageToMap(slug, image)));
    }

    public static void respondProjectImage(final Event<HttpObject, HttpObject> event, final ProjectImageContent image) {
        ResponseUtil.respondBytes(event, 200, image.contentType(), image.data());
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
        payload.put("descriptions", descriptionsToMap(project.descriptions()));
        payload.put("status", project.status());
        payload.put("founder", founderToMap(project.founder()));
        payload.put("openRoles", project.openRoles().stream().map(ProjectFeedUtil::openRoleToMap).toList());
        payload.put("links", project.links().stream().map(ProjectFeedUtil::projectLinkToMap).toList());
        payload.put("images", project.images().stream().map(image -> projectImageToMap(project.slug(), image)).toList());
        payload.put("createdAt", project.createdAt().toString());
        return payload;
    }

    private static Map<String, Object> projectDetailsToMap(final ProjectDetails project, final boolean canManage) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", project.id());
        payload.put("canManage", canManage);
        payload.put("slug", project.slug());
        payload.put("title", project.title());
        payload.put("descriptions", descriptionsToMap(project.descriptions()));
        payload.put("status", project.status());
        payload.put("founder", founderToMap(project.founder()));
        payload.put("openRoles", project.openRoles().stream().map(ProjectFeedUtil::openRoleToMap).toList());
        payload.put("links", project.links().stream().map(ProjectFeedUtil::projectLinkToMap).toList());
        payload.put("images", project.images().stream().map(image -> projectImageToMap(project.slug(), image)).toList());
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

    private static Map<String, Object> projectLinkToMap(final ProjectLink link) {
        return Map.of(
            "label", link.label(),
            "url", link.url()
        );
    }

    private static Map<String, Object> projectImageToMap(final String slug, final ProjectImage image) {
        return Map.of(
            "id", image.id(),
            "url", "/api/projects/" + slug + "/images/" + image.id(),
            "contentType", image.contentType(),
            "sizeBytes", image.sizeBytes(),
            "altText", image.altText(),
            "createdAt", image.createdAt().toString()
        );
    }

    private static ProjectDescriptions descriptionsFrom(final LinkedTypeMap body) {
        final LinkedTypeMap descriptions = body.asMap("descriptions");
        if (descriptions != null) {
            return new ProjectDescriptions(
                nullableTrim(descriptions.asString("de")),
                nullableTrim(descriptions.asString("en"))
            );
        }

        return new ProjectDescriptions(null, null);
    }

    private static Map<String, Object> descriptionsToMap(final ProjectDescriptions descriptions) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("de", descriptions.de());
        payload.put("en", descriptions.en());
        return payload;
    }

    private static String safeTrim(final String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullableTrim(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static LinkedTypeMap linkedTypeMapFromListItem(final Object item) {
        if (item instanceof LinkedTypeMap linkedTypeMap) {
            return linkedTypeMap;
        }
        if (item instanceof Map<?, ?> map) {
            return new LinkedTypeMap(map);
        }
        throw new IllegalArgumentException("Invalid project payload item shape");
    }
}
