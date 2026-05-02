package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.auth.AuthRepository;
import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.auth.SessionUser;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
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
    private String basePath;

    public ProjectFeedService(final DatabaseRuntime databaseRuntime) {
        this.databaseRuntime = databaseRuntime;
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
            ProjectFeedUtil.respondOptions(event);
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
            default -> ProjectFeedUtil.respondMethodNotAllowed(event);
        }
    }

    protected void handleGet(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        switch (route) {
            case ProjectFeedUtil.ProjectFeedRoute __ ->
                ProjectFeedUtil.respondProjects(event, ProjectFeedRepository.listProjects(databaseRuntime.dataSource()));
            case ProjectFeedUtil.ProjectDetailsRoute detailsRoute ->
                ProjectFeedRepository.findProjectBySlug(databaseRuntime.dataSource(), detailsRoute.slug())
                    .ifPresentOrElse(
                        project -> ProjectFeedUtil.respondProjectDetails(event, project),
                        () -> ProjectFeedUtil.respondNotFound(event, "Project not found.")
                    );
            case ProjectFeedUtil.NoMatch __ -> {
            }
        }
    }

    protected void handlePost(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        if (!(route instanceof ProjectFeedUtil.ProjectFeedRoute)) {
            ProjectFeedUtil.respondMethodNotAllowed(event);
            return;
        }

        final Optional<SessionUser> sessionUser = currentSessionUser(event.payload());
        if (sessionUser.isEmpty()) {
            ProjectFeedUtil.respondUnauthorized(event, "You must be signed in to create a project.");
            return;
        }

        final LinkedTypeMap body = event.payload().bodyAsMap();
        final ProjectInput input = ProjectFeedUtil.projectInputFrom(body);
        final Optional<String> validation = ProjectFeedUtil.validateProjectInput(input);
        if (validation.isPresent()) {
            ProjectFeedUtil.respondBadRequest(event, validation.get());
            return;
        }

        final String slug = ProjectFeedRepository.createProject(databaseRuntime.dataSource(), sessionUser.get().id(), input);
        ProjectFeedUtil.respondProjectSaved(event, slug, 201);
    }

    protected void handlePut(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        if (!(route instanceof ProjectFeedUtil.ProjectDetailsRoute(String slug))) {
            ProjectFeedUtil.respondMethodNotAllowed(event);
            return;
        }

        final Optional<SessionUser> sessionUser = currentSessionUser(event.payload());
        if (sessionUser.isEmpty()) {
            ProjectFeedUtil.respondUnauthorized(event, "You must be signed in to edit a project.");
            return;
        }

        final Optional<ProjectDetails> existingProject = ProjectFeedRepository.findProjectBySlug(databaseRuntime.dataSource(), slug);
        if (existingProject.isEmpty()) {
            ProjectFeedUtil.respondNotFound(event, "Project not found.");
            return;
        }
        if (existingProject.get().ownerUserId() != sessionUser.get().id()) {
            ProjectFeedUtil.respondForbidden(event, "Only the project owner can edit this project.");
            return;
        }

        final LinkedTypeMap body = event.payload().bodyAsMap();
        final ProjectInput input = ProjectFeedUtil.projectInputFrom(body);
        final Optional<String> validation = ProjectFeedUtil.validateProjectInput(input);
        if (validation.isPresent()) {
            ProjectFeedUtil.respondBadRequest(event, validation.get());
            return;
        }

        ProjectFeedRepository.updateProject(databaseRuntime.dataSource(), existingProject.get().id(), input);
        ProjectFeedUtil.respondProjectSaved(event, existingProject.get().slug(), 200);
    }

    protected void handleDelete(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        if (!(route instanceof ProjectFeedUtil.ProjectDetailsRoute(String slug))) {
            ProjectFeedUtil.respondMethodNotAllowed(event);
            return;
        }

        final Optional<SessionUser> sessionUser = currentSessionUser(event.payload());
        if (sessionUser.isEmpty()) {
            ProjectFeedUtil.respondUnauthorized(event, "You must be signed in to delete a project.");
            return;
        }

        final Optional<ProjectDetails> existingProject = ProjectFeedRepository.findProjectBySlug(databaseRuntime.dataSource(), slug);
        if (existingProject.isEmpty()) {
            ProjectFeedUtil.respondNotFound(event, "Project not found.");
            return;
        }
        if (existingProject.get().ownerUserId() != sessionUser.get().id()) {
            ProjectFeedUtil.respondForbidden(event, "Only the project owner can delete this project.");
            return;
        }

        ProjectFeedRepository.deleteProject(databaseRuntime.dataSource(), existingProject.get().id());
        ProjectFeedUtil.respondDeleted(event);
    }

    private Optional<SessionUser> currentSessionUser(final HttpObject request) {
        return AuthUtil.readSessionToken(request)
            .flatMap(token -> AuthRepository.findSessionUserByTokenHash(databaseRuntime.dataSource(), AuthUtil.hashToken(token)));
    }
}
