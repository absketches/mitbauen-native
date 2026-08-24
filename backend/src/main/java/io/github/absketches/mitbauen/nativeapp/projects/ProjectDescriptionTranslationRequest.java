package io.github.absketches.mitbauen.nativeapp.projects;

public record ProjectDescriptionTranslationRequest(
    long projectId,
    ProjectDescriptions descriptions
) {
}
