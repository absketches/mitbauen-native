package io.github.absketches.mitbauen.nativeapp.projects;

import java.util.Optional;

public interface ProjectDescriptionTranslator {

    Optional<String> translate(String text, String sourceLanguage, String targetLanguage);

    default String provider() {
        return "unknown";
    }

    default String model() {
        return "unknown";
    }

    static ProjectDescriptionTranslator disabled() {
        return new ProjectDescriptionTranslator() {
            @Override
            public Optional<String> translate(final String text, final String sourceLanguage, final String targetLanguage) {
                return Optional.empty();
            }

            @Override
            public String provider() {
                return "disabled";
            }

            @Override
            public String model() {
                return "disabled";
            }
        };
    }
}
