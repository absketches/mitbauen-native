package io.github.absketches.mitbauen.nativeapp.auth;

public interface VerificationEmailSender {

    void sendVerificationEmail(String recipientEmail, String recipientName, String verificationUrl);

    default void sendPasswordResetEmail(final String recipientEmail, final String recipientName, final String passwordResetUrl) {
    }
}
