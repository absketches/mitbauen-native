package io.github.absketches.mitbauen.nativeapp.jobs;

import berlin.yuna.typemap.model.LinkedTypeMap;
import io.github.absketches.mitbauen.nativeapp.http.ResponseUtil;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JobsUtil {

    public static final String JOBS_PATH = "/api/jobs";
    public static final String JOB_APPLICATIONS_PATH = "/api/jobs/applications";
    public static final String JOBS_AUTH_REQUIRED_CODE = "JOBS_AUTH_REQUIRED";
    public static final String JOBS_EMAIL_UNVERIFIED_CODE = "JOBS_EMAIL_UNVERIFIED";
    public static final String JOB_APPLICATION_ROLE_INVALID_CODE = "JOB_APPLICATION_ROLE_INVALID";
    public static final String JOB_APPLICATION_FIT_INVALID_CODE = "JOB_APPLICATION_FIT_INVALID";
    public static final String JOB_APPLICATION_AVAILABILITY_INVALID_CODE = "JOB_APPLICATION_AVAILABILITY_INVALID";
    public static final String JOB_APPLICATION_NOT_FOUND_CODE = "JOB_APPLICATION_NOT_FOUND";
    public static final String JOB_APPLICATION_DUPLICATE_CODE = "JOB_APPLICATION_DUPLICATE";
    public static final String JOB_APPLICATION_SEND_FAILED_CODE = "JOB_APPLICATION_SEND_FAILED";
    public static final String METHOD_NOT_ALLOWED_CODE = "METHOD_NOT_ALLOWED";
    public static final int APPLICATION_FIT_MIN_LENGTH = 20;
    public static final int APPLICATION_FIT_MAX_LENGTH = 2000;
    public static final int APPLICATION_AVAILABILITY_MAX_LENGTH = 500;

    private JobsUtil() {
    }

    public static boolean matches(final HttpObject request) {
        final String path = request.uri().getPath();
        return JOBS_PATH.equals(path) || JOB_APPLICATIONS_PATH.equals(path);
    }

    public static boolean matchesJobsList(final HttpObject request) {
        return JOBS_PATH.equals(request.uri().getPath());
    }

    public static boolean matchesApplications(final HttpObject request) {
        return JOB_APPLICATIONS_PATH.equals(request.uri().getPath());
    }

    public static JobApplicationInput applicationInputFrom(final LinkedTypeMap body) {
        return new JobApplicationInput(
            body.asLong("roleId"),
            safeTrim(body.asString("fit")),
            safeTrim(body.asString("availability"))
        );
    }

    public static void respondJobs(final Event<HttpObject, HttpObject> event, final List<JobListing> jobs) {
        ResponseUtil.respondOk(event, Map.of("jobs", jobs.stream().map(JobsUtil::jobToMap).toList()));
    }

    public static void respondOptions(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondOptions(event);
    }

    public static void respondMethodNotAllowed(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondMethodNotAllowed(event, METHOD_NOT_ALLOWED_CODE);
    }

    public static void respondApplicationSent(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondOk(event, Map.of("sent", true));
    }

    public static void respondBadRequest(final Event<HttpObject, HttpObject> event, final String code) {
        ResponseUtil.respondBadRequest(event, code);
    }

    public static void respondNotFound(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondNotFound(event, JOB_APPLICATION_NOT_FOUND_CODE);
    }

    public static void respondConflict(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondConflict(event, JOB_APPLICATION_DUPLICATE_CODE);
    }

    public static void respondServerError(final Event<HttpObject, HttpObject> event) {
        ResponseUtil.respondServerError(event, JOB_APPLICATION_SEND_FAILED_CODE);
    }

    private static Map<String, Object> jobToMap(final JobListing job) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", job.projectSlug() + "::" + job.roleId());
        payload.put("roleId", job.roleId());
        payload.put("projectSlug", job.projectSlug());
        payload.put("projectTitle", job.projectTitle());
        payload.put("roleTitle", job.roleTitle());
        payload.put("roleCommitment", job.roleCommitment());
        return payload;
    }

    private static String safeTrim(final String value) {
        return value == null ? "" : value.trim();
    }

    public record JobApplicationInput(Long roleId, String fit, String availability) {
    }
}
