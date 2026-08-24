package io.github.absketches.mitbauen.nativeapp;

import io.github.absketches.mitbauen.nativeapp.email.TransactionalEmailRequest;
import io.github.absketches.mitbauen.nativeapp.projects.translation.ProjectDescriptionTranslationRequest;
import org.nanonative.nano.helper.event.model.Channel;

import static org.nanonative.nano.helper.event.model.Channel.registerChannelId;

public final class MitbauenEvents {

    private MitbauenEvents() {
    }

    public static final Channel<ProjectDescriptionTranslationRequest, Void> PROJECT_DESCRIPTION_TRANSLATION_REQUEST =
        registerChannelId("PROJECT_DESCRIPTION_TRANSLATION_REQUEST", ProjectDescriptionTranslationRequest.class);
    public static final Channel<TransactionalEmailRequest, Void> TRANSACTIONAL_EMAIL_REQUEST =
        registerChannelId("TRANSACTIONAL_EMAIL_REQUEST", TransactionalEmailRequest.class);
}
