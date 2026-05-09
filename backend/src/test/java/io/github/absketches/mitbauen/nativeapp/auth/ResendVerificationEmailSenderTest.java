package io.github.absketches.mitbauen.nativeapp.auth;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResendVerificationEmailSenderTest {

    @Test
    void serializesVerificationEmailPayloadWithTypeMapJson() {
        final String payload = ResendVerificationEmailSender.verificationEmailPayload(
            "Mitbauen <no-reply@mail.mitbauen.space>",
            "builder@example.test",
            "Ada \"Builder\" <script>",
            "https://www.mitbauen.space/verify-email?token=verify_a%2Bb"
        );

        final LinkedTypeMap body = new LinkedTypeMap(payload);
        final TypeList recipients = body.asList("to");

        assertThat(body.asString("from")).isEqualTo("Mitbauen <no-reply@mail.mitbauen.space>");
        assertThat(recipients).containsExactly("builder@example.test");
        assertThat(body.asString("subject")).isEqualTo("Verify your email address");
        assertThat(body.asString("html"))
            .contains("Hello Ada &quot;Builder&quot; &lt;script&gt;,")
            .contains("https://www.mitbauen.space/verify-email?token=verify_a%2Bb");
        assertThat(body.asString("text"))
            .contains("Hello Ada \"Builder\" <script>,")
            .contains("https://www.mitbauen.space/verify-email?token=verify_a%2Bb");
    }
}
