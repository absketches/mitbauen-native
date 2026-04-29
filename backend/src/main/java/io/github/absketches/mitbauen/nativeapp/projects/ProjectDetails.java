package io.github.absketches.mitbauen.nativeapp.projects;

import java.time.Instant;
import java.util.List;

public record ProjectDetails(
    long id,
    long ownerUserId,
    String slug,
    String title,
    String description,
    String status,
    FounderInfo founder,
    List<OpenRole> openRoles,
    Instant createdAt,
    Instant updatedAt
) {
}
