package io.github.absketches.mitbauen.nativeapp.projects;

import io.github.absketches.mitbauen.nativeapp.projects.links.ProjectLink;
import io.github.absketches.mitbauen.nativeapp.projects.media.ProjectImage;

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
    List<ProjectLink> links,
    List<ProjectImage> images,
    Instant createdAt,
    Instant updatedAt
) {
}
