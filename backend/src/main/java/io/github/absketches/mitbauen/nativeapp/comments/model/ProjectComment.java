package io.github.absketches.mitbauen.nativeapp.comments.model;

import java.time.Instant;

public record ProjectComment(
    long id,
    String body,
    String authorPublicId,
    String authorDisplayName,
    Instant createdAt
) {
}
