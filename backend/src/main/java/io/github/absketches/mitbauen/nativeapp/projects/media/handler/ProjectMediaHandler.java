package io.github.absketches.mitbauen.nativeapp.projects.media.handler;

import io.github.absketches.mitbauen.nativeapp.projects.media.model.ProjectImage;
import io.github.absketches.mitbauen.nativeapp.projects.media.repository.ProjectImagesRepository;

import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.auth.model.SessionUser;
import io.github.absketches.mitbauen.nativeapp.http.ResponseUtil;
import io.github.absketches.mitbauen.nativeapp.projects.model.ProjectDetails;
import io.github.absketches.mitbauen.nativeapp.projects.repository.ProjectFeedRepository;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedUtil;
import io.github.absketches.mitbauen.nativeapp.util.TextUtil;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import javax.sql.DataSource;
import java.util.Optional;

public class ProjectMediaHandler {

    private final DataSource dataSource;

    public ProjectMediaHandler(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void handleGetProjectImage(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.ProjectImageRoute imageRoute) {
        ProjectImagesRepository.findProjectImageContentBySlug(dataSource, imageRoute.slug(), imageRoute.imageId())
            .ifPresentOrElse(
                image -> ResponseUtil.respondBytes(event, 200, image.contentType(), image.data()),
                () -> ResponseUtil.respondNotFound(event, ProjectImageValidator.PROJECT_IMAGE_NOT_FOUND_CODE)
            );
    }

    public void handlePostProjectImage(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.ProjectImagesRoute imagesRoute) {
        final Optional<SessionUser> sessionUser = AuthUtil.verifiedSessionUser(
            event,
            dataSource,
            ProjectImageValidator.PROJECT_IMAGE_AUTH_REQUIRED_CODE,
            ProjectImageValidator.PROJECT_IMAGE_EMAIL_UNVERIFIED_CODE
        );
        if (sessionUser.isEmpty()) {
            return;
        }

        final Optional<ProjectDetails> existingProject = ProjectFeedRepository.findProjectBySlug(dataSource, imagesRoute.slug());
        if (existingProject.isEmpty()) {
            ResponseUtil.respondNotFound(event, ProjectFeedUtil.PROJECT_NOT_FOUND_CODE);
            return;
        }
        if (!requireOwner(event, sessionUser.get(), existingProject.get(), ProjectImageValidator.PROJECT_IMAGE_OWNER_REQUIRED_CODE)) {
            return;
        }

        final String contentType = ProjectImageValidator.canonicalContentType(event.payload().header("content-type"));
        final byte[] body = event.payload().body();
        final String altText = TextUtil.trimToEmpty(event.payload().header("x-image-alt"));
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
            ProjectImageValidator.PROJECT_IMAGES_MAX_COUNT
        );
        if (image.isEmpty()) {
            ResponseUtil.respondBadRequest(event, ProjectImageValidator.PROJECT_IMAGES_MAX_CODE);
            return;
        }
        ResponseUtil.respondCreated(event, ProjectFeedUtil.projectImageSavedPayload(existingProject.get().slug(), image.get()));
    }

    public void handleDeleteProjectImage(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.ProjectImageRoute imageRoute) {
        final Optional<SessionUser> sessionUser = AuthUtil.verifiedSessionUser(
            event,
            dataSource,
            ProjectImageValidator.PROJECT_IMAGE_AUTH_REQUIRED_CODE,
            ProjectImageValidator.PROJECT_IMAGE_EMAIL_UNVERIFIED_CODE
        );
        if (sessionUser.isEmpty()) {
            return;
        }

        final Optional<ProjectDetails> existingProject = ProjectFeedRepository.findProjectBySlug(dataSource, imageRoute.slug());
        if (existingProject.isEmpty()) {
            ResponseUtil.respondNotFound(event, ProjectFeedUtil.PROJECT_NOT_FOUND_CODE);
            return;
        }
        if (!requireOwner(event, sessionUser.get(), existingProject.get(), ProjectImageValidator.PROJECT_IMAGE_OWNER_REQUIRED_CODE)) {
            return;
        }

        if (!ProjectImagesRepository.deleteProjectImage(dataSource, existingProject.get().id(), imageRoute.imageId())) {
            ResponseUtil.respondNotFound(event, ProjectImageValidator.PROJECT_IMAGE_NOT_FOUND_CODE);
            return;
        }
        ResponseUtil.respondEmpty(event, 204);
    }

    private static boolean requireOwner(
        final Event<HttpObject, HttpObject> event,
        final SessionUser sessionUser,
        final ProjectDetails project,
        final String ownerRequiredCode
    ) {
        if (project.ownerUserId() == sessionUser.id()) {
            return true;
        }
        ResponseUtil.respondForbidden(event, ownerRequiredCode);
        return false;
    }
}
