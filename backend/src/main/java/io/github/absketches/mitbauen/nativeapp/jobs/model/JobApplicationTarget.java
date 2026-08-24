package io.github.absketches.mitbauen.nativeapp.jobs.model;

public record JobApplicationTarget(
    long roleId,
    String roleTitle,
    String projectTitle,
    String ownerName,
    String ownerEmail
) {
}
