package io.github.absketches.mitbauen.nativeapp.auth;

public interface TransactionalEmailSender {

    void sendVerificationEmail(String recipientEmail, String recipientName, String verificationUrl);

    default void sendPasswordResetEmail(final String recipientEmail, final String recipientName, final String passwordResetUrl) {
    }

    default void sendRoleApplicationEmail(
        final String recipientEmail,
        final String recipientName,
        final String projectTitle,
        final String roleTitle,
        final String applicantName,
        final String applicantEmail,
        final String fit,
        final String availability
    ) {
    }
}
