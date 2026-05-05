package io.github.absketches.mitbauen.nativeapp.auth;

import java.util.Locale;
import java.util.Optional;

public record EmailVerificationSettings(
    String publicBaseUrl,
    String emailFrom,
    String resendApiKey
) {

    public static final String ENV_APP_PUBLIC_BASE_URL = "app_public_base_url";
    public static final String ENV_APP_EMAIL_FROM = "app_email_from";
    public static final String ENV_RESEND_API_KEY = "resend_api_key";

    public static EmailVerificationSettings fromEnvironment() {
        return new EmailVerificationSettings(
            requiredEnv(ENV_APP_PUBLIC_BASE_URL),
            requiredEnv(ENV_APP_EMAIL_FROM),
            requiredEnv(ENV_RESEND_API_KEY)
        );
    }

    private static String requiredEnv(final String key) {
        return optionalEnv(key)
            .orElseThrow(() -> new IllegalStateException("Missing required environment variable: " + key));
    }

    private static Optional<String> optionalEnv(final String key) {
        final String directValue = System.getenv(key);
        if (directValue != null && !directValue.isBlank()) {
            return Optional.of(directValue);
        }

        final String upperCaseValue = System.getenv(key.toUpperCase(Locale.ROOT));
        if (upperCaseValue != null && !upperCaseValue.isBlank()) {
            return Optional.of(upperCaseValue);
        }
        return Optional.empty();
    }
}
