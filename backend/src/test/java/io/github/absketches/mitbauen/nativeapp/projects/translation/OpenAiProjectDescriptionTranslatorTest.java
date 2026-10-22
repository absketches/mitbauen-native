package io.github.absketches.mitbauen.nativeapp.projects.translation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiProjectDescriptionTranslatorTest {

    @Test
    void readsTranslatedTextFromNestedResponseOutput() {
        final String responseBody = """
            {
              "output": [
                {"type": "reasoning"},
                {"content": [{"type": "output_text", "text": "  Gemeinsam bauen.  "}]}
              ]
            }
            """;

        assertThat(OpenAiProjectDescriptionTranslator.translatedTextFrom(responseBody))
            .contains("Gemeinsam bauen.");
    }

    @Test
    void returnsEmptyWhenResponseHasNoTranslatedText() {
        assertThat(OpenAiProjectDescriptionTranslator.translatedTextFrom("{}"))
            .isEmpty();
        assertThat(OpenAiProjectDescriptionTranslator.translatedTextFrom("{\"output_text\": \"  \"}"))
            .isEmpty();
    }

    @Test
    void decodesUnicodeEscapesFromTranslatedText() {
        final String responseBody = """
            {
              "output_text": "Dies ist ein Projekt \\u00fcber die ber\\u00fchmte S\\u00e4ngerin Adel und ihr beliebtestes Lied \\u201eHello\\u201c."
            }
            """;

        assertThat(OpenAiProjectDescriptionTranslator.translatedTextFrom(responseBody))
            .contains("Dies ist ein Projekt über die berühmte Sängerin Adel und ihr beliebtestes Lied „Hello“.");
    }
}
