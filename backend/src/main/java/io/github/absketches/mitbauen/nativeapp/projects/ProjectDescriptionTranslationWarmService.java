package io.github.absketches.mitbauen.nativeapp.projects;

import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.MitbauenEvents;
import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;

import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;

public class ProjectDescriptionTranslationWarmService extends Service {

    public static final String CONFIG_OPENAI_API_KEY = registerConfig("app_translation_openai_api_key", "OpenAI API key for project description fallback translations");
    public static final String CONFIG_OPENAI_MODEL = registerConfig("app_translation_openai_model", "OpenAI model for project description fallback translations");

    private final ProjectDescriptionTranslationWarmer translationWarmer;
    private String apiKey;
    private String model;
    private ProjectDescriptionTranslator translator = ProjectDescriptionTranslator.disabled();

    public ProjectDescriptionTranslationWarmService(final DatabaseRuntime databaseRuntime) {
        this.translationWarmer = new ProjectDescriptionTranslationWarmer(databaseRuntime.dataSource());
    }

    @Override
    public void start() {
        translator = OpenAiProjectDescriptionTranslator.fromConfig(apiKey, model);
        context.info(() -> "[{}] started", name());
    }

    @Override
    public void stop() {
        context.info(() -> "[{}] stopped", name());
    }

    @Override
    public Object onFailure(final Event<?, ?> error) {
        return error.payload();
    }

    @Override
    public void onEvent(final Event<?, ?> event) {
        event.channel(MitbauenEvents.PROJECT_DESCRIPTION_TRANSLATION_REQUEST).ifPresent(this::handleTranslationRequest);
    }

    @Override
    public void configure(final TypeMapI<?> changes, final TypeMapI<?> merged) {
        apiKey = merged.asStringOpt(CONFIG_OPENAI_API_KEY).filter(value -> !value.isBlank()).orElse(null);
        model = merged.asStringOpt(CONFIG_OPENAI_MODEL).filter(value -> !value.isBlank()).orElse(null);
    }

    private void handleTranslationRequest(final Event<ProjectDescriptionTranslationRequest, Void> event) {
        final ProjectDescriptionTranslationRequest request = event.payload();
        try {
            translationWarmer.warmProjectTranslations(request.projectId(), request.descriptions(), translator);
        } catch (RuntimeException exception) {
            context.warn(exception, () -> "Unable to warm project description translation");
        }
    }
}
