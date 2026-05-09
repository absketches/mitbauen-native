package io.github.absketches.mitbauen.nativeapp.auth;

import berlin.yuna.typemap.logic.JsonEncoder;
import berlin.yuna.typemap.model.LinkedTypeMap;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class ResendVerificationEmailSender implements VerificationEmailSender {

    private static final URI RESEND_EMAILS_ENDPOINT = URI.create("https://api.resend.com/emails");

    private final HttpClient httpClient;
    private final String apiKey;
    private final String emailFrom;

    public ResendVerificationEmailSender(final String apiKey, final String emailFrom) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.apiKey = apiKey;
        this.emailFrom = emailFrom;
    }

    @Override
    public void sendVerificationEmail(final String recipientEmail, final String recipientName, final String verificationUrl) {
        sendEmail(verificationEmailPayload(emailFrom, recipientEmail, recipientName, verificationUrl));
    }

    @Override
    public void sendPasswordResetEmail(final String recipientEmail, final String recipientName, final String passwordResetUrl) {
        sendEmail(passwordResetEmailPayload(emailFrom, recipientEmail, recipientName, passwordResetUrl));
    }

    private void sendEmail(final String payload) {
        final HttpRequest request = HttpRequest.newBuilder(RESEND_EMAILS_ENDPOINT)
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Resend rejected email: " + response.statusCode() + " " + response.body());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to send email", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to send email", exception);
        }
    }

    public static String verificationEmailPayload(
        final String emailFrom,
        final String recipientEmail,
        final String recipientName,
        final String verificationUrl
    ) {
        return JsonEncoder.toJson(LinkedTypeMap.linkedMapOf(
            "from", emailFrom,
            "to", List.of(recipientEmail),
            "subject", "Verify your email address",
            "html", htmlBody(recipientName, verificationUrl),
            "text", textBody(recipientName, verificationUrl)
        ));
    }

    public static String passwordResetEmailPayload(
        final String emailFrom,
        final String recipientEmail,
        final String recipientName,
        final String passwordResetUrl
    ) {
        return JsonEncoder.toJson(LinkedTypeMap.linkedMapOf(
            "from", emailFrom,
            "to", List.of(recipientEmail),
            "subject", "Reset your password",
            "html", passwordResetHtmlBody(recipientName, passwordResetUrl),
            "text", passwordResetTextBody(recipientName, passwordResetUrl)
        ));
    }

    private static String htmlBody(final String recipientName, final String verificationUrl) {
        return """
            <div style="font-family:Arial,sans-serif;line-height:1.6;color:#111111">
              <p>Hello %s,</p>
              <p>Please verify your Mitbauen email address to activate your account.</p>
              <p><a href="%s">Verify email</a></p>
              <p>If the button does not work, open this link:</p>
              <p>%s</p>
              <hr />
              <p>Hallo %s,</p>
              <p>Bitte bestätige deine E-Mail-Adresse, um dein Mitbauen-Konto zu aktivieren.</p>
              <p><a href="%s">E-Mail bestätigen</a></p>
              <p>Falls der Link nicht klickbar ist, öffne bitte diese Adresse:</p>
              <p>%s</p>
            </div>
            """.formatted(
            htmlEscape(recipientName),
            htmlEscape(verificationUrl),
            htmlEscape(verificationUrl),
            htmlEscape(recipientName),
            htmlEscape(verificationUrl),
            htmlEscape(verificationUrl)
        );
    }

    private static String textBody(final String recipientName, final String verificationUrl) {
        return """
            Hello %s,

            Please verify your Mitbauen email address to activate your account:
            %s

            ---

            Hallo %s,

            bitte bestätige deine E-Mail-Adresse, um dein Mitbauen-Konto zu aktivieren:
            %s
            """.formatted(recipientName, verificationUrl, recipientName, verificationUrl);
    }

    private static String passwordResetHtmlBody(final String recipientName, final String passwordResetUrl) {
        return """
            <div style="font-family:Arial,sans-serif;line-height:1.6;color:#111111">
              <p>Hello %s,</p>
              <p>Use this link to set a new password for your Mitbauen Lokal account.</p>
              <p><a href="%s">Reset password</a></p>
              <p>If the button does not work, open this link:</p>
              <p>%s</p>
              <hr />
              <p>Hallo %s,</p>
              <p>Nutze diesen Link, um ein neues Passwort für dein Mitbauen Lokal Konto zu setzen.</p>
              <p><a href="%s">Passwort zurücksetzen</a></p>
              <p>Falls der Link nicht klickbar ist, öffne bitte diese Adresse:</p>
              <p>%s</p>
            </div>
            """.formatted(
            htmlEscape(recipientName),
            htmlEscape(passwordResetUrl),
            htmlEscape(passwordResetUrl),
            htmlEscape(recipientName),
            htmlEscape(passwordResetUrl),
            htmlEscape(passwordResetUrl)
        );
    }

    private static String passwordResetTextBody(final String recipientName, final String passwordResetUrl) {
        return """
            Hello %s,

            use this link to set a new password for your Mitbauen Lokal account:
            %s

            ---

            Hallo %s,

            nutze diesen Link, um ein neues Passwort für dein Mitbauen Lokal Konto zu setzen:
            %s
            """.formatted(recipientName, passwordResetUrl, recipientName, passwordResetUrl);
    }

    private static String htmlEscape(final String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
