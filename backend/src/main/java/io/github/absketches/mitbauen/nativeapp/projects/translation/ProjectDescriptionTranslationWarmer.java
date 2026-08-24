package io.github.absketches.mitbauen.nativeapp.projects.translation;

import io.github.absketches.mitbauen.nativeapp.projects.model.ProjectDescriptions;

import javax.sql.DataSource;
import java.util.Optional;

public class ProjectDescriptionTranslationWarmer {

    private static final String GERMAN = "de";
    private static final String ENGLISH = "en";

    private final DataSource dataSource;

    public ProjectDescriptionTranslationWarmer(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int warmProjectTranslations(
        final long projectId,
        final ProjectDescriptions descriptions,
        final ProjectDescriptionTranslator translator
    ) {
        if (descriptions.de() == null && descriptions.en() != null) {
            return warmTranslation(projectId, descriptions.en(), ENGLISH, GERMAN, translator);
        }
        if (descriptions.en() == null && descriptions.de() != null) {
            return warmTranslation(projectId, descriptions.de(), GERMAN, ENGLISH, translator);
        }
        return 0;
    }

    public int backfillMissingTranslations(final ProjectDescriptionTranslator translator) {
        int savedTranslations = 0;
        for (ProjectDescriptionTranslationRepository.ProjectDescriptionTranslationCandidate candidate
            : ProjectDescriptionTranslationRepository.listTranslationCandidates(dataSource)) {
            savedTranslations += warmProjectTranslations(candidate.projectId(), candidate.descriptions(), translator);
        }
        return savedTranslations;
    }

    private int warmTranslation(
        final long projectId,
        final String sourceText,
        final String sourceLanguage,
        final String targetLanguage,
        final ProjectDescriptionTranslator translator
    ) {
        final String sourceTextHash = ProjectDescriptionTranslation.sourceTextHash(sourceText);
        final Optional<ProjectDescriptionTranslation> existingTranslation =
            ProjectDescriptionTranslationRepository.findTranslation(dataSource, projectId, sourceLanguage, targetLanguage);
        if (existingTranslation
            .filter(translation -> sourceTextHash.equals(translation.sourceTextHash()))
            .isPresent()) {
            return 0;
        }

        final Optional<String> translatedText = translator.translate(sourceText, sourceLanguage, targetLanguage);
        if (translatedText.isEmpty()) {
            return 0;
        }
        ProjectDescriptionTranslationRepository.upsertTranslation(
            dataSource,
            projectId,
            new ProjectDescriptionTranslation(
                sourceLanguage,
                targetLanguage,
                sourceTextHash,
                translatedText.get(),
                translator.provider(),
                translator.model()
            )
        );
        return 1;
    }
}
