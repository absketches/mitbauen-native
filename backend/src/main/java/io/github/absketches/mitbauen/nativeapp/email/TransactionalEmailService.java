package io.github.absketches.mitbauen.nativeapp.email;

import berlin.yuna.typemap.model.TypeMapI;
import io.github.absketches.mitbauen.nativeapp.MitbauenEvents;
import org.nanonative.nano.core.model.Service;
import org.nanonative.nano.helper.event.model.Event;

public class TransactionalEmailService extends Service {

    private final TransactionalEmailSender emailSender;

    public TransactionalEmailService(final TransactionalEmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public void start() {
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
        event.channel(MitbauenEvents.TRANSACTIONAL_EMAIL_REQUEST).ifPresent(this::handleEmailRequest);
    }

    @Override
    public void configure(final TypeMapI<?> changes, final TypeMapI<?> merged) {
    }

    private void handleEmailRequest(final Event<TransactionalEmailRequest, Void> event) {
        final TransactionalEmailRequest request = event.payload();
        try {
            request.sendWith(emailSender);
            request.result().complete(true);
        } catch (RuntimeException exception) {
            context.warn(exception, () -> "Unable to send transactional email");
            request.result().complete(false);
        }
    }

}
