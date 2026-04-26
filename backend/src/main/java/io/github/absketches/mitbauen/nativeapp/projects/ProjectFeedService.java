package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;
import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

public class ProjectFeedService extends Service {

    public static final String CONFIG_PROJECT_FEED_PATH = registerConfig("project_feed_path", "Public project feed API path");
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
    }

    @Override
    public Object onFailure(final Event<?, ?> error) {
        error.channel(EVENT_HTTP_REQUEST).ifPresent(httpEvent -> handleHttpFailure(httpEvent, error.error()));
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
            handleOptions(event, route);
            return;
        }
        handleHttpRequest(event, route);
    }

    protected void handleHttpRequest(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        switch (event.payload().methodType()) {
            case GET -> handleGet(event, route);
            default -> handleMethodNotAllowed(event, route);
        }
    }

    protected void handleGet(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        switch (route) {
            case ProjectFeedUtil.ProjectFeedRoute __ -> ProjectFeedUtil.respondProjects(event, ProjectFeedRepository.listProjects(databaseRuntime.dataSource()));
            case ProjectFeedUtil.NoMatch __ -> {
            }
        }
    }

    protected void handleOptions(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        switch (route) {
            case ProjectFeedUtil.ProjectFeedRoute __ -> ProjectFeedUtil.respondOptions(event);
            case ProjectFeedUtil.NoMatch __ -> {
            }
        }
    }

    protected void handleMethodNotAllowed(final Event<HttpObject, HttpObject> event, final ProjectFeedUtil.RoutesMatch route) {
        switch (route) {
            case ProjectFeedUtil.ProjectFeedRoute __ -> ProjectFeedUtil.respondMethodNotAllowed(event);
            case ProjectFeedUtil.NoMatch __ -> {
            }
        }
    }

    protected void handleHttpFailure(final Event<HttpObject, HttpObject> event, final Throwable error) {
        switch (ProjectFeedUtil.match(event.payload(), basePath)) {
            case ProjectFeedUtil.ProjectFeedRoute __ -> ProjectFeedUtil.respondFailure(event, error);
            case ProjectFeedUtil.NoMatch __ -> {
            }
        }
    }
}
