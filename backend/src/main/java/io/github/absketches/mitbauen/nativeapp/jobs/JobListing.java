package io.github.absketches.mitbauen.nativeapp.jobs;

public record JobListing(
    long roleId,
    String projectSlug,
    String projectTitle,
    String roleTitle,
    String roleCommitment
) {
}
