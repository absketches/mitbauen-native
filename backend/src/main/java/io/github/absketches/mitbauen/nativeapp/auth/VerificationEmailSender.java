package io.github.absketches.mitbauen.nativeapp.auth;

public interface VerificationEmailSender {

    void sendVerificationEmail(String recipientEmail, String recipientName, String verificationUrl);
}
