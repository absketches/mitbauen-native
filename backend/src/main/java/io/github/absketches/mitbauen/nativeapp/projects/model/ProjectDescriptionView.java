package io.github.absketches.mitbauen.nativeapp.projects.model;

public record ProjectDescriptionView(
    String text,
    String language,
    String originalLanguage,
    boolean translated,
    String source
) {
}
