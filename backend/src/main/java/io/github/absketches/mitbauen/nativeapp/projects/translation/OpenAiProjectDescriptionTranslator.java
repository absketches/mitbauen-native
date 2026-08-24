package io.github.absketches.mitbauen.nativeapp.projects.translation;

import berlin.yuna.typemap.logic.JsonDecoder;
import berlin.yuna.typemap.logic.JsonEncoder;
import berlin.yuna.typemap.model.LinkedTypeMap;
import berlin.yuna.typemap.model.TypeList;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class OpenAiProjectDescriptionTranslator implements ProjectDescriptionTranslator {

    private static final URI DEFAULT_RESPONSES_ENDPOINT = URI.create("https://api.openai.com/v1/responses");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Map<TranslationKey, String> cache = new ConcurrentHashMap<>();

    OpenAiProjectDescriptionTranslator(
        final HttpClient httpClient,
        final URI endpoint,
        final String apiKey,
        final String model
    ) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
    }

    public static ProjectDescriptionTranslator fromConfig(final String apiKey, final String model) {
        if (isBlank(apiKey) || isBlank(model)) {
            return ProjectDescriptionTranslator.disabled();
        }
        return new OpenAiProjectDescriptionTranslator(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            DEFAULT_RESPONSES_ENDPOINT,
            apiKey,
            model
        );
    }

    @Override
    public Optional<String> translate(final String text, final String sourceLanguage, final String targetLanguage) {
        if (text == null || text.isBlank() || sourceLanguage.equals(targetLanguage)) {
            return Optional.empty();
        }

        final TranslationKey key = new TranslationKey(text, sourceLanguage, targetLanguage);
        final String cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }

        final Optional<String> translated = requestTranslation(text, sourceLanguage, targetLanguage);
        translated.ifPresent(value -> cache.put(key, value));
        return translated;
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public String model() {
        return model;
    }

    private Optional<String> requestTranslation(final String text, final String sourceLanguage, final String targetLanguage) {
        final HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(translationPayload(text, sourceLanguage, targetLanguage)))
            .build();

        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return translatedTextFrom(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String translationPayload(final String text, final String sourceLanguage, final String targetLanguage) {
        return JsonEncoder.toJson(LinkedTypeMap.linkedMapOf(
            "model", model,
            "store", false,
            "instructions", """
                Translate project descriptions for Mitbauen. Preserve Markdown, links, paragraph breaks, tone, and meaning.
                Return only the translated description text, with no preface and no disclaimer.
                """,
            "input", "Translate this project description from %s to %s:\n\n%s".formatted(
                languageName(sourceLanguage),
                languageName(targetLanguage),
                text
            )
        ));
    }

    static Optional<String> translatedTextFrom(final String responseBody) {
        final LinkedTypeMap response = JsonDecoder.jsonMapOf(responseBody);
        final String outputText = clean(response.asString("output_text"));
        if (outputText != null) {
            return Optional.of(outputText);
        }

        final TypeList output = response.asList("output");
        if (output == null) {
            return Optional.empty();
        }
        return output.stream()
            .filter(Map.class::isInstance)
            .map(item -> new LinkedTypeMap((Map<?, ?>) item).asList("content"))
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .filter(Map.class::isInstance)
            .map(item -> new LinkedTypeMap((Map<?, ?>) item).asString("text"))
            .map(OpenAiProjectDescriptionTranslator::clean)
            .filter(Objects::nonNull)
            .findFirst();
    }

    private static String clean(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = decodeUnicodeEscapes(value).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String decodeUnicodeEscapes(final String value) {
        final StringBuilder decoded = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            if (isUnicodeEscapeAt(value, index)) {
                decoded.append((char) Integer.parseInt(value.substring(index + 2, index + 6), 16));
                index += 5;
            } else {
                decoded.append(value.charAt(index));
            }
        }
        return decoded.toString();
    }

    private static boolean isUnicodeEscapeAt(final String value, final int index) {
        if (index + 5 >= value.length() || value.charAt(index) != '\\' || value.charAt(index + 1) != 'u') {
            return false;
        }
        for (int offset = index + 2; offset <= index + 5; offset++) {
            if (Character.digit(value.charAt(offset), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String languageName(final String language) {
        return switch (language) {
            case "de" -> "German";
            case "en" -> "English";
            default -> language;
        };
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private record TranslationKey(String text, String sourceLanguage, String targetLanguage) {
    }
}
