package io.github.absketches.mitbauen.nativeapp.projects;

import io.github.absketches.mitbauen.nativeapp.MitbauenApplication;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;

public class ProjectDescriptionTranslationBackfill {

    private static final String CONFIG_OPENAI_API_KEY = "app_translation_openai_api_key";
    private static final String CONFIG_OPENAI_MODEL = "app_translation_openai_model";

    private ProjectDescriptionTranslationBackfill() {
    }

    public static void main(final String[] ignoredArgs) {
        run();
    }

    public static void run() {
        DatabaseRuntime databaseRuntime = null;
        try {
            databaseRuntime = MitbauenApplication.dbStartup();
            final ProjectDescriptionTranslator translator = translatorFromEnvironment();
            final int savedTranslations = new ProjectDescriptionTranslationWarmer(databaseRuntime.dataSource())
                .backfillMissingTranslations(translator);
            System.out.printf("Backfilled [%d] project description translations%n", savedTranslations);
        } finally {
            if (databaseRuntime != null) {
                databaseRuntime.stop();
            }
        }
    }

    private static ProjectDescriptionTranslator translatorFromEnvironment() {
        final String apiKey = System.getenv(CONFIG_OPENAI_API_KEY);
        final String model = System.getenv(CONFIG_OPENAI_MODEL);
        if (isBlank(apiKey) || isBlank(model)) {
            throw new IllegalStateException(
                "Missing app_translation_openai_api_key or app_translation_openai_model for translation backfill"
            );
        }
        return OpenAiProjectDescriptionTranslator.fromConfig(apiKey, model);
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
