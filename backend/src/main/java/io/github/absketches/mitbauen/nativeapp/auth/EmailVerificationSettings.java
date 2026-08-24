package io.github.absketches.mitbauen.nativeapp.auth;

import static org.nanonative.nano.helper.config.ConfigRegister.registerConfig;

public record EmailVerificationSettings(
    String publicBaseUrl,
    String emailFrom,
    String resendApiKey
) {

    public static final String CONFIG_APP_PUBLIC_BASE_URL = registerConfig("app_public_base_url", "Public base URL for links in transactional emails");
    public static final String CONFIG_APP_EMAIL_FROM = registerConfig("app_email_from", "Sender address for transactional emails");
    public static final String CONFIG_RESEND_API_KEY = registerConfig("resend_api_key", "Resend API key for transactional emails");
}
