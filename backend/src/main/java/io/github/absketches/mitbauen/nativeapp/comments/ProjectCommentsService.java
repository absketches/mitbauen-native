package io.github.absketches.mitbauen.nativeapp.comments;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.auth.SessionUser;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectDetails;
import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedRepository;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Optional;

import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

public class ProjectCommentsService extends Service {

    private final DatabaseRuntime databaseRuntime;

    public ProjectCommentsService(final DatabaseRuntime databaseRuntime) {
        this.databaseRuntime = databaseRuntime;
    }

    @Override
    public void start() {
        context.info(() -> "[{}] started", name());
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
    }

    protected void handleHttpEvent(final Event<HttpObject, HttpObject> event) {
        final ProjectCommentsUtil.RouteMatch route = ProjectCommentsUtil.match(event.payload());
        if (route instanceof ProjectCommentsUtil.NoMatch) {
            return;
        }
        if (event.payload().isMethodOptions()) {
            ProjectCommentsUtil.respondOptions(event);
            return;
        }
        handleHttpRequest(event, route);
    }

    protected void handleHttpRequest(final Event<HttpObject, HttpObject> event, final ProjectCommentsUtil.RouteMatch route) {
        switch (event.payload().methodType()) {
            case GET -> handleGet(event, route);
            case POST -> handlePost(event, route);
            default -> ProjectCommentsUtil.respondMethodNotAllowed(event);
        }
    }

    protected void handleGet(final Event<HttpObject, HttpObject> event, final ProjectCommentsUtil.RouteMatch route) {
        if (route instanceof ProjectCommentsUtil.ProjectCommentsRoute commentsRoute) {
            handleCommentsLookup(event, commentsRoute.slug());
            return;
        }
        ProjectCommentsUtil.respondMethodNotAllowed(event);
    }

    protected void handlePost(final Event<HttpObject, HttpObject> event, final ProjectCommentsUtil.RouteMatch route) {
        switch (route) {
            case ProjectCommentsUtil.ProjectCommentsRoute commentsRoute -> handleCommentCreate(event, commentsRoute.slug());
            case ProjectCommentsUtil.ProjectCommentsReadRoute readRoute -> handleCommentsRead(event, readRoute.slug());
            default -> ProjectCommentsUtil.respondMethodNotAllowed(event);
        }
    }

    private void handleCommentsLookup(final Event<HttpObject, HttpObject> event, final String slug) {
        final Optional<SessionUser> sessionUser = verifiedSessionUser(event);
        if (sessionUser.isEmpty()) {
            return;
        }
        final Optional<ProjectDetails> project = findProject(event, slug);
        project.ifPresent(details -> ProjectCommentsUtil.respondComments(
            event,
            ProjectCommentsRepository.listComments(databaseRuntime.dataSource(), details.id())
        ));
    }

    private void handleCommentCreate(final Event<HttpObject, HttpObject> event, final String slug) {
        final Optional<SessionUser> sessionUser = verifiedSessionUser(event);
        if (sessionUser.isEmpty()) {
            return;
        }
        final Optional<ProjectDetails> project = findProject(event, slug);
        if (project.isEmpty()) {
            return;
        }
        final LinkedTypeMap body = event.payload().bodyAsMap();
        final String commentBody = ProjectCommentsUtil.commentBodyFrom(body);
        final Optional<String> validation = ProjectCommentsUtil.validateCommentBody(commentBody);
        if (validation.isPresent()) {
            ProjectCommentsUtil.respondBadRequest(event, validation.get());
            return;
        }
        final ProjectComment comment = ProjectCommentsRepository.createComment(
            databaseRuntime.dataSource(),
            project.get().id(),
            sessionUser.get().id(),
            commentBody
        );
        ProjectCommentsUtil.respondCommentCreated(event, comment);
    }

    private void handleCommentsRead(final Event<HttpObject, HttpObject> event, final String slug) {
        final Optional<SessionUser> sessionUser = verifiedSessionUser(event);
        if (sessionUser.isEmpty()) {
            return;
        }
        final Optional<ProjectDetails> project = findProject(event, slug);
        if (project.isEmpty()) {
            return;
        }
        ProjectCommentsRepository.markCommentsRead(databaseRuntime.dataSource(), project.get().id(), sessionUser.get().id());
        ProjectCommentsUtil.respondRead(event);
    }

    private Optional<ProjectDetails> findProject(final Event<HttpObject, HttpObject> event, final String slug) {
        final Optional<ProjectDetails> project = ProjectFeedRepository.findProjectBySlug(databaseRuntime.dataSource(), slug);
        if (project.isEmpty()) {
            ProjectCommentsUtil.respondNotFound(event, ProjectCommentsUtil.PROJECT_NOT_FOUND_CODE);
        }
        return project;
    }

    private Optional<SessionUser> verifiedSessionUser(final Event<HttpObject, HttpObject> event) {
        final Optional<SessionUser> sessionUser = AuthUtil.currentSessionUser(event.payload(), databaseRuntime.dataSource());
        if (sessionUser.isEmpty()) {
            ProjectCommentsUtil.respondUnauthorized(event);
            return Optional.empty();
        }
        if (!sessionUser.get().emailVerified()) {
            ProjectCommentsUtil.respondForbidden(event);
            return Optional.empty();
        }
        return sessionUser;
    }
}
