package io.github.absketches.mitbauen.nativeapp.projects.translation;

import io.github.absketches.mitbauen.nativeapp.projects.model.ProjectDescriptionView;
import io.github.absketches.mitbauen.nativeapp.projects.model.ProjectDescriptions;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProjectDescriptionResolver {

    private static final String GERMAN = "de";
    private static final String ENGLISH = "en";
    private static final String SOURCE_ORIGINAL = "original";
    private static final String SOURCE_TOOL_TRANSLATION = "tool_translation";
    private static final String SOURCE_MISSING = "missing";

    private ProjectDescriptionResolver() {
    }

    public static Map<String, ProjectDescriptionView> viewsFor(
        final ProjectDescriptions descriptions,
        final Map<String, ProjectDescriptionTranslation> translations
    ) {
        final Map<String, ProjectDescriptionView> views = new LinkedHashMap<>();
        views.put(GERMAN, viewFor(descriptions, translations, GERMAN));
        views.put(ENGLISH, viewFor(descriptions, translations, ENGLISH));
        return views;
    }

    private static ProjectDescriptionView viewFor(
        final ProjectDescriptions descriptions,
        final Map<String, ProjectDescriptionTranslation> translations,
        final String language
    ) {
        final String original = descriptionFor(descriptions, language);
        if (original != null) {
            return new ProjectDescriptionView(original, language, language, false, SOURCE_ORIGINAL);
        }

        final String sourceLanguage = otherLanguage(language);
        final String sourceText = descriptionFor(descriptions, sourceLanguage);
        if (sourceText == null) {
            return new ProjectDescriptionView(null, language, null, false, SOURCE_MISSING);
        }

        final ProjectDescriptionTranslation translation = translations.get(language);
        if (
            translation != null
                && sourceLanguage.equals(translation.sourceLanguage())
                && language.equals(translation.targetLanguage())
                && ProjectDescriptionTranslation.sourceTextHash(sourceText).equals(translation.sourceTextHash())
        ) {
            return new ProjectDescriptionView(translation.translatedText(), language, sourceLanguage, true, SOURCE_TOOL_TRANSLATION);
        }
        return new ProjectDescriptionView(null, language, sourceLanguage, false, SOURCE_MISSING);
    }

    private static String descriptionFor(final ProjectDescriptions descriptions, final String language) {
        return switch (language) {
            case GERMAN -> descriptions.de();
            case ENGLISH -> descriptions.en();
            default -> throw new IllegalArgumentException("Unsupported description language: " + language);
        };
    }

    private static String otherLanguage(final String language) {
        return GERMAN.equals(language) ? ENGLISH : GERMAN;
    }
}
