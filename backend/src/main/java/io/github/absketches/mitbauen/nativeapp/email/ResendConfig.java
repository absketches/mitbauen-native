package io.github.absketches.mitbauen.nativeapp.email;

import io.github.absketches.mitbauen.nativeapp.util.EnvUtil;

public record ResendConfig(String emailFrom, String apiKey) {

    public static final String ENV_APP_EMAIL_FROM = "app_email_from";
    public static final String ENV_RESEND_API_KEY = "resend_api_key";

    public static ResendConfig fromEnvironment() {
        return new ResendConfig(
            EnvUtil.requiredEnv(ENV_APP_EMAIL_FROM),
            EnvUtil.requiredEnv(ENV_RESEND_API_KEY)
        );
    }
}
