package io.github.absketches.mitbauen.nativeapp.jobs;

import io.github.absketches.mitbauen.nativeapp.jobs.model.JobListing;

import berlin.yuna.typemap.model.LinkedTypeMap;
import io.github.absketches.mitbauen.nativeapp.util.TextUtil;
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
            TextUtil.trimToEmpty(body.asString("fit")),
            TextUtil.trimToEmpty(body.asString("availability"))
        );
    }

    public static Map<String, Object> jobsPayload(final List<JobListing> jobs) {
        return Map.of("jobs", jobs.stream().map(JobsUtil::jobToMap).toList());
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

    public record JobApplicationInput(Long roleId, String fit, String availability) {
    }
}
