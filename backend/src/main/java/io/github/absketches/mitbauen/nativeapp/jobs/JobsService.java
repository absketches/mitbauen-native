package io.github.absketches.mitbauen.nativeapp.jobs;

import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.auth.SessionUser;
import io.github.absketches.mitbauen.nativeapp.auth.TransactionalEmailSender;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Optional;

import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

public class JobsService extends Service {

    private final DatabaseRuntime databaseRuntime;
    private final TransactionalEmailSender emailSender;

    public JobsService(final DatabaseRuntime databaseRuntime, final TransactionalEmailSender emailSender) {
        this.databaseRuntime = databaseRuntime;
        this.emailSender = emailSender;
    }

    @Override
    public void start() {
        context.info(() -> "[{}] started on path {}", name(), JobsUtil.JOBS_PATH);
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
        if (!JobsUtil.matches(event.payload())) {
            return;
        }
        if (event.payload().isMethodOptions()) {
            JobsUtil.respondOptions(event);
            return;
        }

        if (JobsUtil.matchesJobsList(event.payload())) {
            handleJobsList(event);
            return;
        }
        if (JobsUtil.matchesApplications(event.payload())) {
            handleApplication(event);
        }
    }

    private void handleJobsList(final Event<HttpObject, HttpObject> event) {
        if (!event.payload().isMethodGet()) {
            JobsUtil.respondMethodNotAllowed(event);
            return;
        }

        final Optional<SessionUser> sessionUser = AuthUtil.verifiedSessionUser(
            event,
            databaseRuntime.dataSource(),
            JobsUtil.JOBS_AUTH_REQUIRED_CODE,
            JobsUtil.JOBS_EMAIL_UNVERIFIED_CODE
        );
        if (sessionUser.isEmpty()) {
            return;
        }

        JobsUtil.respondJobs(event, JobsRepository.listJobs(databaseRuntime.dataSource()));
    }

    private void handleApplication(final Event<HttpObject, HttpObject> event) {
        if (!event.payload().isMethodPost()) {
            JobsUtil.respondMethodNotAllowed(event);
            return;
        }

        final Optional<SessionUser> sessionUser = AuthUtil.verifiedSessionUser(
            event,
            databaseRuntime.dataSource(),
            JobsUtil.JOBS_AUTH_REQUIRED_CODE,
            JobsUtil.JOBS_EMAIL_UNVERIFIED_CODE
        );
        if (sessionUser.isEmpty()) {
            return;
        }

        final JobsUtil.JobApplicationInput input = JobsUtil.applicationInputFrom(AuthUtil.bodyAsMap(event.payload()));
        if (input.roleId() == null || input.roleId() <= 0) {
            JobsUtil.respondBadRequest(event, JobsUtil.JOB_APPLICATION_ROLE_INVALID_CODE);
            return;
        }
        if (input.fit().length() < JobsUtil.APPLICATION_FIT_MIN_LENGTH || input.fit().length() > JobsUtil.APPLICATION_FIT_MAX_LENGTH) {
            JobsUtil.respondBadRequest(event, JobsUtil.JOB_APPLICATION_FIT_INVALID_CODE);
            return;
        }
        if (input.availability().length() > JobsUtil.APPLICATION_AVAILABILITY_MAX_LENGTH) {
            JobsUtil.respondBadRequest(event, JobsUtil.JOB_APPLICATION_AVAILABILITY_INVALID_CODE);
            return;
        }

        final Optional<JobApplicationTarget> target = JobsRepository.findApplicationTarget(databaseRuntime.dataSource(), input.roleId());
        if (target.isEmpty()) {
            JobsUtil.respondNotFound(event);
            return;
        }

        final JobApplicationCreateResult createResult = JobsRepository.createApplication(
            databaseRuntime.dataSource(),
            input.roleId(),
            sessionUser.get().id(),
            input.fit(),
            input.availability()
        );
        if (createResult == JobApplicationCreateResult.DUPLICATE_OR_UNAVAILABLE) {
            JobsUtil.respondConflict(event);
            return;
        }

        try {
            emailSender.sendRoleApplicationEmail(
                target.get().ownerEmail(),
                target.get().ownerName(),
                target.get().projectTitle(),
                target.get().roleTitle(),
                sessionUser.get().displayName(),
                sessionUser.get().email(),
                input.fit(),
                input.availability()
            );
            JobsUtil.respondApplicationSent(event);
        } catch (RuntimeException exception) {
            try {
                JobsRepository.deleteApplication(databaseRuntime.dataSource(), input.roleId(), sessionUser.get().id());
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            context.error(() -> "Unable to send role application email", exception);
            JobsUtil.respondServerError(event);
        }
    }
}
