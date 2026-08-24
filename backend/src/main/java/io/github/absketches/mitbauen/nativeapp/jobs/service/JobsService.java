package io.github.absketches.mitbauen.nativeapp.jobs.service;

import io.github.absketches.mitbauen.nativeapp.jobs.model.JobApplicationCreateResult;
import io.github.absketches.mitbauen.nativeapp.jobs.model.JobApplicationTarget;
import io.github.absketches.mitbauen.nativeapp.jobs.repository.JobsRepository;

import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.MitbauenEvents;
import io.github.absketches.mitbauen.nativeapp.auth.AuthUtil;
import io.github.absketches.mitbauen.nativeapp.auth.model.SessionUser;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import io.github.absketches.mitbauen.nativeapp.email.TransactionalEmailRequest;
import io.github.absketches.mitbauen.nativeapp.http.ResponseUtil;
import io.github.absketches.mitbauen.nativeapp.jobs.JobsUtil;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

public class JobsService extends Service {

    private static final long EMAIL_DELIVERY_WAIT_SECONDS = 30;

    private final DatabaseRuntime databaseRuntime;

    public JobsService(final DatabaseRuntime databaseRuntime) {
        this.databaseRuntime = databaseRuntime;
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
            ResponseUtil.respondOptions(event);
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
            ResponseUtil.respondMethodNotAllowed(event, ResponseUtil.METHOD_NOT_ALLOWED_CODE);
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

        ResponseUtil.respondOk(event, JobsUtil.jobsPayload(JobsRepository.listJobs(databaseRuntime.dataSource())));
    }

    private void handleApplication(final Event<HttpObject, HttpObject> event) {
        if (!event.payload().isMethodPost()) {
            ResponseUtil.respondMethodNotAllowed(event, ResponseUtil.METHOD_NOT_ALLOWED_CODE);
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

        final JobsUtil.JobApplicationInput input = JobsUtil.applicationInputFrom(event.payload().bodyAsMap());
        if (input.roleId() == null || input.roleId() <= 0) {
            ResponseUtil.respondBadRequest(event, JobsUtil.JOB_APPLICATION_ROLE_INVALID_CODE);
            return;
        }
        if (input.fit().length() < JobsUtil.APPLICATION_FIT_MIN_LENGTH || input.fit().length() > JobsUtil.APPLICATION_FIT_MAX_LENGTH) {
            ResponseUtil.respondBadRequest(event, JobsUtil.JOB_APPLICATION_FIT_INVALID_CODE);
            return;
        }
        if (input.availability().length() > JobsUtil.APPLICATION_AVAILABILITY_MAX_LENGTH) {
            ResponseUtil.respondBadRequest(event, JobsUtil.JOB_APPLICATION_AVAILABILITY_INVALID_CODE);
            return;
        }

        final Optional<JobApplicationTarget> target = JobsRepository.findApplicationTarget(databaseRuntime.dataSource(), input.roleId());
        if (target.isEmpty()) {
            ResponseUtil.respondNotFound(event, JobsUtil.JOB_APPLICATION_NOT_FOUND_CODE);
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
            ResponseUtil.respondConflict(event, JobsUtil.JOB_APPLICATION_DUPLICATE_CODE);
            return;
        }

        try {
            sendEmail(new TransactionalEmailRequest.RoleApplicationEmail(
                target.get().ownerEmail(),
                target.get().ownerName(),
                target.get().projectTitle(),
                target.get().roleTitle(),
                sessionUser.get().displayName(),
                sessionUser.get().email(),
                input.fit(),
                input.availability(),
                new CompletableFuture<>()
            ));
            ResponseUtil.respondOk(event, Map.of("sent", true));
        } catch (RuntimeException exception) {
            try {
                JobsRepository.deleteApplication(databaseRuntime.dataSource(), input.roleId(), sessionUser.get().id());
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            context.error(() -> "Unable to send role application email", exception);
            ResponseUtil.respondServerError(event, JobsUtil.JOB_APPLICATION_SEND_FAILED_CODE);
        }
    }

    private void sendEmail(final TransactionalEmailRequest request) {
        context.newEvent(MitbauenEvents.TRANSACTIONAL_EMAIL_REQUEST)
            .payload(() -> request)
            .async(true)
            .send();
        try {
            if (!request.result().get(EMAIL_DELIVERY_WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Unable to send transactional email");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for transactional email", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Unable to send transactional email", exception);
        }
    }
}
