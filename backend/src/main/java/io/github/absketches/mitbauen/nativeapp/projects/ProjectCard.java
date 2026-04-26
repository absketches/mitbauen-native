package io.github.absketches.mitbauen.nativeapp.projects;

import java.time.Instant;
import java.util.List;

public record ProjectCard(
    long id,
    String slug,
    String title,
    String summary,
    String status,
    FounderInfo founder,
    List<OpenRole> openRoles,
    Instant createdAt
) {
}
