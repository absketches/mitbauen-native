package io.github.absketches.mitbauen.nativeapp.auth;

import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResendTransactionalEmailSenderTest {

    @Test
    void serializesVerificationEmailPayloadWithTypeMapJson() {
        final String payload = ResendTransactionalEmailSender.verificationEmailPayload(
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

    @Test
    void serializesPasswordResetEmailPayloadWithTypeMapJson() {
        final String payload = ResendTransactionalEmailSender.passwordResetEmailPayload(
            "Mitbauen <no-reply@mail.mitbauen.space>",
            "builder@example.test",
            "Ada \"Builder\" <script>",
            "https://www.mitbauen.space/reset-password?token=reset_a%2Bb"
        );

        final LinkedTypeMap body = new LinkedTypeMap(payload);
        final TypeList recipients = body.asList("to");

        assertThat(body.asString("from")).isEqualTo("Mitbauen <no-reply@mail.mitbauen.space>");
        assertThat(recipients).containsExactly("builder@example.test");
        assertThat(body.asString("subject")).isEqualTo("Reset your password");
        assertThat(body.asString("html"))
            .contains("Hello Ada &quot;Builder&quot; &lt;script&gt;,")
            .contains("https://www.mitbauen.space/reset-password?token=reset_a%2Bb");
        assertThat(body.asString("text"))
            .contains("Hello Ada \"Builder\" <script>,")
            .contains("https://www.mitbauen.space/reset-password?token=reset_a%2Bb");
    }

    @Test
    void serializesRoleApplicationEmailPayloadWithEscapedHtml() {
        final String payload = ResendTransactionalEmailSender.roleApplicationEmailPayload(
            "Mitbauen <no-reply@mail.mitbauen.space>",
            "owner@example.test",
            "Owner \"Lead\" <script>",
            "Repair <Library>",
            "Tool \"Librarian\"",
            "Applicant <Builder>",
            "applicant@example.test",
            "I can help with <cataloging>.\nI have done this before.",
            ""
        );

        final LinkedTypeMap body = new LinkedTypeMap(payload);
        final TypeList recipients = body.asList("to");

        assertThat(body.asString("from")).isEqualTo("Mitbauen <no-reply@mail.mitbauen.space>");
        assertThat(recipients).containsExactly("owner@example.test");
        assertThat(body.asString("subject")).isEqualTo("New application for Tool \"Librarian\"");
        assertThat(body.asString("html"))
            .contains("Hello Owner &quot;Lead&quot; &lt;script&gt;,")
            .contains("Applicant &lt;Builder&gt; applied for <strong>Tool &quot;Librarian&quot;</strong>")
            .contains("Repair &lt;Library&gt;")
            .contains("I can help with &lt;cataloging&gt;.<br />I have done this before.")
            .contains("Not provided");
        assertThat(body.asString("text"))
            .contains("Applicant <Builder> applied for Tool \"Librarian\" on Repair <Library>.")
            .contains("I can help with <cataloging>.\nI have done this before.")
            .contains("Not provided");
    }
}
