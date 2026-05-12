package io.github.absketches.mitbauen.nativeapp.projects.media;

public record ProjectImageContent(
    long id,
    long projectId,
    String contentType,
    byte[] data
) {
}
