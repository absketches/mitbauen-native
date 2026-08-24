package io.github.absketches.mitbauen.nativeapp.jobs.model;

public record JobListing(
    long roleId,
    String projectSlug,
    String projectTitle,
    String roleTitle,
    String roleCommitment
) {
}
