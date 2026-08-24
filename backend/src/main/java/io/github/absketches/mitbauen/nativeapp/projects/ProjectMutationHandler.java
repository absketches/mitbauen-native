package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.LinkedTypeMap;
import io.github.absketches.mitbauen.nativeapp.MitbauenEvents;
import io.github.absketches.mitbauen.nativeapp.auth.SessionUser;
import io.github.absketches.mitbauen.nativeapp.http.ResponseUtil;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import javax.sql.DataSource;
import java.util.Optional;

public class ProjectMutationHandler {

    private final DataSource dataSource;
    private final ProjectAccessGuard accessGuard;

    public ProjectMutationHandler(
        final DataSource dataSource,
        final ProjectAccessGuard accessGuard
    ) {
        this.dataSource = dataSource;
        this.accessGuard = accessGuard;
    }

    public void handleCreateProject(final Event<HttpObject, HttpObject> event) {
        final Optional<SessionUser> sessionUser = accessGuard.verifiedSession(
            event,
            ProjectFeedUtil.PROJECT_CREATE_AUTH_REQUIRED_CODE,
            ProjectFeedUtil.PROJECT_CREATE_EMAIL_UNVERIFIED_CODE
        );
        if (sessionUser.isEmpty()) {
            return;
        }

        final Optional<ProjectInput> input = validProjectInputOrRespondBadRequest(event);
        if (input.isEmpty()) {
            return;
        }

        final ProjectFeedRepository.CreatedProject project = ProjectFeedRepository.createProject(dataSource, sessionUser.get().id(), input.get());
        requestTranslation(event, project.id(), input.get().descriptions());
        ResponseUtil.respondJson(event, 201, ProjectFeedUtil.projectSavedPayload(project.slug()));
    }

    public void handleUpdateProject(final Event<HttpObject, HttpObject> event, final String slug) {
        final Optional<SessionUser> sessionUser = accessGuard.verifiedSession(
            event,
            ProjectFeedUtil.PROJECT_EDIT_AUTH_REQUIRED_CODE,
            ProjectFeedUtil.PROJECT_EDIT_EMAIL_UNVERIFIED_CODE
        );
        if (sessionUser.isEmpty()) {
            return;
        }

        final Optional<ProjectDetails> existingProject = ProjectFeedRepository.findProjectBySlug(dataSource, slug);
        if (existingProject.isEmpty()) {
            ResponseUtil.respondNotFound(event, ProjectFeedUtil.PROJECT_NOT_FOUND_CODE);
            return;
        }
        if (!accessGuard.requireOwner(event, sessionUser.get(), existingProject.get(), ProjectFeedUtil.PROJECT_EDIT_OWNER_REQUIRED_CODE)) {
            return;
        }

        final Optional<ProjectInput> input = validProjectInputOrRespondBadRequest(event);
        if (input.isEmpty()) {
            return;
        }

        final boolean descriptionsChanged = !input.get().descriptions().equals(existingProject.get().descriptions());
        ProjectFeedRepository.updateProject(dataSource, existingProject.get().id(), input.get(), descriptionsChanged);
        if (descriptionsChanged) {
            requestTranslation(event, existingProject.get().id(), input.get().descriptions());
        }
        ResponseUtil.respondOk(event, ProjectFeedUtil.projectSavedPayload(existingProject.get().slug()));
    }

    public void handleDeleteProject(final Event<HttpObject, HttpObject> event, final String slug) {
        final Optional<SessionUser> sessionUser = accessGuard.verifiedSession(
            event,
            ProjectFeedUtil.PROJECT_DELETE_AUTH_REQUIRED_CODE,
            ProjectFeedUtil.PROJECT_DELETE_EMAIL_UNVERIFIED_CODE
        );
        if (sessionUser.isEmpty()) {
            return;
        }

        final Optional<ProjectDetails> existingProject = ProjectFeedRepository.findProjectBySlug(dataSource, slug);
        if (existingProject.isEmpty()) {
            ResponseUtil.respondNotFound(event, ProjectFeedUtil.PROJECT_NOT_FOUND_CODE);
            return;
        }
        if (!accessGuard.requireOwner(event, sessionUser.get(), existingProject.get(), ProjectFeedUtil.PROJECT_DELETE_OWNER_REQUIRED_CODE)) {
            return;
        }

        ProjectFeedRepository.deleteProject(dataSource, existingProject.get().id());
        ResponseUtil.respondEmpty(event, 204);
    }

    private void requestTranslation(
            final Event<HttpObject, HttpObject> event,
            final long projectId,
            final ProjectDescriptions descriptions
    ) {
        event.context()
                .newEvent(MitbauenEvents.PROJECT_DESCRIPTION_TRANSLATION_REQUEST)
                .payload(() -> new ProjectDescriptionTranslationRequest(projectId, descriptions))
                .async(true)
                .send();
    }

    private static Optional<ProjectInput> validProjectInputOrRespondBadRequest(final Event<HttpObject, HttpObject> event) {
        try {
            final LinkedTypeMap body = event.payload().bodyAsMap();
            final ProjectInput input = ProjectFeedUtil.projectInputFrom(body);
            final Optional<String> validation = ProjectInputValidator.validate(input);
            if (validation.isPresent()) {
                ResponseUtil.respondBadRequest(event, validation.get());
                return Optional.empty();
            }
            return Optional.of(input);
        } catch (RuntimeException exception) {
            ResponseUtil.respondBadRequest(event, ProjectFeedUtil.PROJECT_PAYLOAD_INVALID_CODE);
            return Optional.empty();
        }
    }
}
