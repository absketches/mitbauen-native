package io.github.absketches.mitbauen.nativeapp.projects.media;

import java.time.Instant;

public record ProjectImage(
    long id,
    String contentType,
    int sizeBytes,
    String altText,
    Instant createdAt
) {
}
