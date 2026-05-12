package io.github.absketches.mitbauen.nativeapp.projects;

import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.auth.SessionUser;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import javax.sql.DataSource;
import java.util.Optional;

public class ProjectAccessGuard {

    private final DataSource dataSource;

    public ProjectAccessGuard(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<SessionUser> verifiedSession(
        final Event<HttpObject, HttpObject> event,
        final String authRequiredCode,
        final String emailUnverifiedCode
    ) {
        final Optional<SessionUser> sessionUser = AuthUtil.currentSessionUser(event.payload(), dataSource);
        if (sessionUser.isEmpty()) {
            ProjectFeedUtil.respondUnauthorized(event, authRequiredCode);
            return Optional.empty();
        }
        if (!sessionUser.get().emailVerified()) {
            ProjectFeedUtil.respondForbidden(event, emailUnverifiedCode);
            return Optional.empty();
        }
        return sessionUser;
    }

    public boolean requireOwner(
        final Event<HttpObject, HttpObject> event,
        final SessionUser sessionUser,
        final ProjectDetails project,
        final String ownerRequiredCode
    ) {
        if (project.ownerUserId() == sessionUser.id()) {
            return true;
        }
        ProjectFeedUtil.respondForbidden(event, ownerRequiredCode);
        return false;
    }
}
