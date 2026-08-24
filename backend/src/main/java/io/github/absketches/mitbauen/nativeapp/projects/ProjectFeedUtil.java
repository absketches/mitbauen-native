package io.github.absketches.mitbauen.nativeapp.projects;

import io.github.absketches.mitbauen.nativeapp.projects.model.FounderInfo;
import io.github.absketches.mitbauen.nativeapp.projects.model.OpenRole;
import io.github.absketches.mitbauen.nativeapp.projects.model.ProjectCard;
import io.github.absketches.mitbauen.nativeapp.projects.model.ProjectDescriptionView;
import io.github.absketches.mitbauen.nativeapp.projects.model.ProjectDescriptions;
import io.github.absketches.mitbauen.nativeapp.projects.model.ProjectDetails;
import io.github.absketches.mitbauen.nativeapp.projects.model.ProjectInput;
import io.github.absketches.mitbauen.nativeapp.projects.translation.ProjectDescriptionResolver;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import io.github.absketches.mitbauen.nativeapp.projects.links.model.ProjectLink;
import io.github.absketches.mitbauen.nativeapp.projects.media.model.ProjectImage;
import io.github.absketches.mitbauen.nativeapp.util.TextUtil;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProjectFeedUtil {

    public static final String PROJECTS_BASE_PATH = "/api/projects";
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
            TextUtil.trimToEmpty(body.asString("title")),
            descriptionsFrom(body),
            TextUtil.trimToEmpty(body.asString("founderRole")),
            TextUtil.trimToEmpty(body.asString("founderCommitment")),
            roles == null ? List.of() : roles.stream()
                .map(ProjectFeedUtil::linkedTypeMapFromListItem)
                .map(role -> new OpenRole(
                    null,
                    TextUtil.trimToEmpty(role.asString("title")),
                    TextUtil.trimToEmpty(role.asString("commitment"))
                ))
                .toList(),
            links == null ? List.of() : links.stream()
                .map(ProjectFeedUtil::linkedTypeMapFromListItem)
                .map(link -> new ProjectLink(
                    TextUtil.trimToEmpty(link.asString("label")),
                    TextUtil.trimToEmpty(link.asString("url"))
                ))
                .toList()
        );
    }

    public static Map<String, Object> projectsPayload(final List<ProjectCard> projects) {
        return Map.of("projects", projects.stream().map(ProjectFeedUtil::projectToMap).toList());
    }

    public static Map<String, Object> projectDetailsPayload(
        final ProjectDetails project,
        final boolean canManage
    ) {
        return Map.of("project", projectDetailsToMap(project, canManage));
    }

    public static Map<String, Object> projectSavedPayload(final String slug) {
        return Map.of("slug", slug);
    }

    public static Map<String, Object> projectImageSavedPayload(final String slug, final ProjectImage image) {
        return Map.of("image", projectImageToMap(slug, image));
    }

    private static Map<String, Object> projectToMap(final ProjectCard project) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", project.id());
        payload.put("slug", project.slug());
        payload.put("title", project.title());
        payload.put("descriptions", descriptionsToMap(project.descriptions()));
        payload.put("descriptionViews", descriptionViewsToMap(ProjectDescriptionResolver.viewsFor(project.descriptions(), project.translations())));
        payload.put("status", project.status());
        payload.put("founder", founderToMap(project.founder()));
        payload.put("openRoles", project.openRoles().stream().map(ProjectFeedUtil::openRoleToMap).toList());
        payload.put("links", project.links().stream().map(ProjectFeedUtil::projectLinkToMap).toList());
        payload.put("images", project.images().stream().map(image -> projectImageToMap(project.slug(), image)).toList());
        payload.put("createdAt", project.createdAt().toString());
        return payload;
    }

    private static Map<String, Object> projectDetailsToMap(
        final ProjectDetails project,
        final boolean canManage
    ) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", project.id());
        payload.put("canManage", canManage);
        payload.put("slug", project.slug());
        payload.put("title", project.title());
        payload.put("descriptions", descriptionsToMap(project.descriptions()));
        payload.put("descriptionViews", descriptionViewsToMap(ProjectDescriptionResolver.viewsFor(project.descriptions(), project.translations())));
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
        final Map<String, Object> payload = new LinkedHashMap<>();
        if (role.id() != null) {
            payload.put("id", role.id());
        }
        payload.put("title", role.title());
        payload.put("commitment", role.commitment());
        return payload;
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
                TextUtil.trimToNull(descriptions.asString("de")),
                TextUtil.trimToNull(descriptions.asString("en"))
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

    private static Map<String, Object> descriptionViewsToMap(final Map<String, ProjectDescriptionView> descriptionViews) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        descriptionViews.forEach((language, view) -> payload.put(language, descriptionViewToMap(view)));
        return payload;
    }

    private static Map<String, Object> descriptionViewToMap(final ProjectDescriptionView view) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", view.text());
        payload.put("language", view.language());
        payload.put("originalLanguage", view.originalLanguage());
        payload.put("translated", view.translated());
        payload.put("source", view.source());
        return payload;
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
