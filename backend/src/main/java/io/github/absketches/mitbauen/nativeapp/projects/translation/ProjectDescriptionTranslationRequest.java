package io.github.absketches.mitbauen.nativeapp.projects.translation;

import io.github.absketches.mitbauen.nativeapp.projects.model.ProjectDescriptions;

public record ProjectDescriptionTranslationRequest(
    long projectId,
    ProjectDescriptions descriptions
) {
}
