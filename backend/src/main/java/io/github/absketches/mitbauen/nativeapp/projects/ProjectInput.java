package io.github.absketches.mitbauen.nativeapp.projects;

import io.github.absketches.mitbauen.nativeapp.projects.links.ProjectLink;

import java.util.List;

public record ProjectInput(
    String title,
    ProjectDescriptions descriptions,
    String founderRole,
    String founderCommitment,
    List<OpenRole> openRoles,
    List<ProjectLink> links
) {
}
