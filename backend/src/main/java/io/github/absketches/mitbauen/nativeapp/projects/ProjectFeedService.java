package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.auth.SessionUser;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.http.ResponseUtil;
import io.github.absketches.mitbauen.nativeapp.projects.media.ProjectMediaHandler;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Optional;

import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;
import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

public class ProjectFeedService extends Service {

    public static final String CONFIG_PROJECT_FEED_PATH = registerConfig("project_feed_path", "Projects API path");
    public static final String DEFAULT_PROJECT_FEED_PATH = "/api/projects";

    private final DatabaseRuntime databaseRuntime;
    private final ProjectAccessGuard accessGuard;
    private final ProjectMutationHandler mutationHandler;
    private final ProjectMediaHandler mediaHandler;
    private String basePath;

    public ProjectFeedService(final DatabaseRuntime databaseRuntime) {
        this.databaseRuntime = databaseRuntime;
        this.accessGuard = new ProjectAccessGuard(databaseRuntime.dataSource());
        this.mutationHandler = new ProjectMutationHandler(
            databaseRuntime.dataSource(),
            accessGuard
        );
        this.mediaHandler = new ProjectMediaHandler(databaseRuntime.dataSource(), accessGuard);
    }

    @Override
    public void start() {
        context.info(() -> "[{}] started on path {}", name(), basePath);
    }

    @Override
    public void stop() {
        context.info(() -> "[{}] stopped", name());
    }

    @Override
    public Object onFailure(final Event<?, ?> error) {
        return error.payload();
    }

    @Override
    public void onEvent(final Event<?, ?> event) {
        event.channel(EVENT_HTTP_REQUEST).ifPresent(this::handleHttpEvent);
    }

    @Override
    public void configure(final TypeMapI<?> changes, final TypeMapI<?> merged) {
        basePath = merged.asStringOpt(CONFIG_PROJECT_FEED_PATH).orElse(DEFAULT_PROJECT_FEED_PATH);
    }

    protected void handleHttpEvent(final Event<HttpObject, HttpObject> event) {
        final ProjectFeedUtil.RoutesMatch route = ProjectFeedUtil.match(event.payload(), basePath);
        if (route instanceof ProjectFeedUtil.NoMatch) {
            return;
        }
        if (event.payload().isMethodOptions()) {
            ResponseUtil.respondOptions(event);
            return;
        }
        handleHttpRequest(event, route);
    }

    protected void handleHttpRequest(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        switch (event.payload().methodType()) {
            case GET -> handleGet(event, route);
            case POST -> handlePost(event, route);
            case PUT -> handlePut(event, route);
            case DELETE -> handleDelete(event, route);
            default -> ResponseUtil.respondMethodNotAllowed(event, ProjectFeedUtil.METHOD_NOT_ALLOWED_CODE);
        }
    }

    protected void handleGet(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        final Optional<SessionUser> sessionUser = accessGuard.verifiedSession(
            event,
            ProjectFeedUtil.PROJECT_VIEW_AUTH_REQUIRED_CODE,
            ProjectFeedUtil.PROJECT_VIEW_EMAIL_UNVERIFIED_CODE
        );
        if (sessionUser.isEmpty()) {
            return;
        }

        switch (route) {
            case ProjectFeedUtil.ProjectFeedRoute __ ->
                ResponseUtil.respondOk(event, ProjectFeedUtil.projectsPayload(ProjectFeedRepository.listProjects(databaseRuntime.dataSource())));
            case ProjectFeedUtil.ProjectDetailsRoute detailsRoute ->
                ProjectFeedRepository.findProjectBySlug(databaseRuntime.dataSource(), detailsRoute.slug())
                    .ifPresentOrElse(
                        project -> ResponseUtil.respondOk(
                            event,
                            ProjectFeedUtil.projectDetailsPayload(
                                project,
                                sessionUser.get().id() == project.ownerUserId() && sessionUser.get().emailVerified()
                            )
                        ),
                        () -> ResponseUtil.respondNotFound(event, ProjectFeedUtil.PROJECT_NOT_FOUND_CODE)
                    );
            case ProjectFeedUtil.ProjectImageRoute imageRoute -> mediaHandler.handleGetProjectImage(event, imageRoute);
            case ProjectFeedUtil.ProjectImagesRoute __ -> ResponseUtil.respondMethodNotAllowed(event, ProjectFeedUtil.METHOD_NOT_ALLOWED_CODE);
            case ProjectFeedUtil.NoMatch __ -> {
            }
        }
    }

    protected void handlePost(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        if (route instanceof ProjectFeedUtil.ProjectImagesRoute imagesRoute) {
            mediaHandler.handlePostProjectImage(event, imagesRoute);
            return;
        }
        if (!(route instanceof ProjectFeedUtil.ProjectFeedRoute)) {
            ResponseUtil.respondMethodNotAllowed(event, ProjectFeedUtil.METHOD_NOT_ALLOWED_CODE);
            return;
        }

        mutationHandler.handleCreateProject(event);
    }

    protected void handlePut(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        if (!(route instanceof ProjectFeedUtil.ProjectDetailsRoute(String slug))) {
            ResponseUtil.respondMethodNotAllowed(event, ProjectFeedUtil.METHOD_NOT_ALLOWED_CODE);
            return;
        }

        mutationHandler.handleUpdateProject(event, slug);
    }

    protected void handleDelete(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        if (route instanceof ProjectFeedUtil.ProjectImageRoute imageRoute) {
            mediaHandler.handleDeleteProjectImage(event, imageRoute);
            return;
        }
        if (!(route instanceof ProjectFeedUtil.ProjectDetailsRoute(String slug))) {
            ResponseUtil.respondMethodNotAllowed(event, ProjectFeedUtil.METHOD_NOT_ALLOWED_CODE);
            return;
        }

        mutationHandler.handleDeleteProject(event, slug);
    }
}
