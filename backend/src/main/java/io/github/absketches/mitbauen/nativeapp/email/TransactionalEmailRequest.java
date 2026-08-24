package io.github.absketches.mitbauen.nativeapp.email;

import java.util.concurrent.CompletableFuture;

public sealed interface TransactionalEmailRequest
    permits TransactionalEmailRequest.VerificationEmail,
    TransactionalEmailRequest.PasswordResetEmail,
    TransactionalEmailRequest.RoleApplicationEmail {

    CompletableFuture<Boolean> result();

    void sendWith(TransactionalEmailSender emailSender);

    record VerificationEmail(
        String recipientEmail,
        String recipientName,
        String verificationUrl,
        CompletableFuture<Boolean> result
    ) implements TransactionalEmailRequest {

        @Override
        public void sendWith(final TransactionalEmailSender emailSender) {
            emailSender.sendVerificationEmail(recipientEmail, recipientName, verificationUrl);
        }
    }

    record PasswordResetEmail(
        String recipientEmail,
        String recipientName,
        String passwordResetUrl,
        CompletableFuture<Boolean> result
    ) implements TransactionalEmailRequest {

        @Override
        public void sendWith(final TransactionalEmailSender emailSender) {
            emailSender.sendPasswordResetEmail(recipientEmail, recipientName, passwordResetUrl);
        }
    }

    record RoleApplicationEmail(
        String recipientEmail,
        String recipientName,
        String projectTitle,
        String roleTitle,
        String applicantName,
        String applicantEmail,
        String fit,
        String availability,
        CompletableFuture<Boolean> result
    ) implements TransactionalEmailRequest {

        @Override
        public void sendWith(final TransactionalEmailSender emailSender) {
            emailSender.sendRoleApplicationEmail(
                recipientEmail,
                recipientName,
                projectTitle,
                roleTitle,
                applicantName,
                applicantEmail,
                fit,
                availability
            );
        }
    }
}
