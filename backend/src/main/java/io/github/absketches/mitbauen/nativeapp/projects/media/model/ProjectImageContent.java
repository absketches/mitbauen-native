package io.github.absketches.mitbauen.nativeapp.projects.media.model;

public record ProjectImageContent(
    long id,
    long projectId,
    String contentType,
    byte[] data
) {
}
