package io.github.absketches.mitbauen.nativeapp.projects.media;

import io.github.absketches.mitbauen.nativeapp.auth.SessionUser;
import io.github.absketches.mitbauen.nativeapp.http.ResponseUtil;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectAccessGuard;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectDetails;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedRepository;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedUtil;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import javax.sql.DataSource;
import java.util.Optional;

public class ProjectMediaService {

    private final DataSource dataSource;
    private final ProjectAccessGuard accessGuard;

    public ProjectMediaService(final DataSource dataSource, final ProjectAccessGuard accessGuard) {
        this.dataSource = dataSource;
        this.accessGuard = accessGuard;
    }

    public void handleGetProjectImage(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.ProjectImageRoute imageRoute) {
        ProjectImagesRepository.findProjectImageContentBySlug(dataSource, imageRoute.slug(), imageRoute.imageId())
            .ifPresentOrElse(
                image -> ResponseUtil.respondBytes(event, 200, image.contentType(), image.data()),
                () -> ResponseUtil.respondNotFound(event, ProjectFeedUtil.PROJECT_IMAGE_NOT_FOUND_CODE)
            );
    }

    public void handlePostProjectImage(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.ProjectImagesRoute imagesRoute) {
        final Optional<SessionUser> sessionUser = accessGuard.verifiedSession(
            event,
            ProjectFeedUtil.PROJECT_IMAGE_AUTH_REQUIRED_CODE,
            ProjectFeedUtil.PROJECT_IMAGE_EMAIL_UNVERIFIED_CODE
        );
        if (sessionUser.isEmpty()) {
            return;
        }

        final Optional<ProjectDetails> existingProject = ProjectFeedRepository.findProjectBySlug(dataSource, imagesRoute.slug());
        if (existingProject.isEmpty()) {
            ResponseUtil.respondNotFound(event, ProjectFeedUtil.PROJECT_NOT_FOUND_CODE);
            return;
        }
        if (!accessGuard.requireOwner(event, sessionUser.get(), existingProject.get(), ProjectFeedUtil.PROJECT_IMAGE_OWNER_REQUIRED_CODE)) {
            return;
        }

        final String contentType = ProjectImageValidator.canonicalContentType(event.payload().header("content-type"));
        final byte[] body = event.payload().body();
        final String altText = safeHeader(event.payload().header("x-image-alt"));
        final Optional<String> validation = ProjectImageValidator.validateUpload(contentType, body, altText);
        if (validation.isPresent()) {
            ResponseUtil.respondBadRequest(event, validation.get());
            return;
        }

        final Optional<ProjectImage> image = ProjectImagesRepository.createProjectImage(
            dataSource,
            existingProject.get().id(),
            contentType,
            body,
            altText,
            ProjectFeedUtil.PROJECT_IMAGES_MAX_COUNT
        );
        if (image.isEmpty()) {
            ResponseUtil.respondBadRequest(event, ProjectFeedUtil.PROJECT_IMAGES_MAX_CODE);
            return;
        }
        ResponseUtil.respondCreated(event, ProjectFeedUtil.projectImageSavedPayload(existingProject.get().slug(), image.get()));
    }

    public void handleDeleteProjectImage(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.ProjectImageRoute imageRoute) {
        final Optional<SessionUser> sessionUser = accessGuard.verifiedSession(
            event,
            ProjectFeedUtil.PROJECT_IMAGE_AUTH_REQUIRED_CODE,
            ProjectFeedUtil.PROJECT_IMAGE_EMAIL_UNVERIFIED_CODE
        );
        if (sessionUser.isEmpty()) {
            return;
        }

        final Optional<ProjectDetails> existingProject = ProjectFeedRepository.findProjectBySlug(dataSource, imageRoute.slug());
        if (existingProject.isEmpty()) {
            ResponseUtil.respondNotFound(event, ProjectFeedUtil.PROJECT_NOT_FOUND_CODE);
            return;
        }
        if (!accessGuard.requireOwner(event, sessionUser.get(), existingProject.get(), ProjectFeedUtil.PROJECT_IMAGE_OWNER_REQUIRED_CODE)) {
            return;
        }

        if (!ProjectImagesRepository.deleteProjectImage(dataSource, existingProject.get().id(), imageRoute.imageId())) {
            ResponseUtil.respondNotFound(event, ProjectFeedUtil.PROJECT_IMAGE_NOT_FOUND_CODE);
            return;
        }
        ResponseUtil.respondEmpty(event, 204);
    }

    private static String safeHeader(final String value) {
        return value == null ? "" : value.trim();
    }
}
