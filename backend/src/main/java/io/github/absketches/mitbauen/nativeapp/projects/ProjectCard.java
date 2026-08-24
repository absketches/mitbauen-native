package io.github.absketches.mitbauen.nativeapp.projects;

import io.github.absketches.mitbauen.nativeapp.projects.links.ProjectLink;
import io.github.absketches.mitbauen.nativeapp.projects.media.ProjectImage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProjectCard(
    long id,
    String slug,
    String title,
    ProjectDescriptions descriptions,
    String status,
    FounderInfo founder,
    List<OpenRole> openRoles,
    List<ProjectLink> links,
    List<ProjectImage> images,
    Map<String, ProjectDescriptionTranslation> translations,
    Instant createdAt
) {
}
