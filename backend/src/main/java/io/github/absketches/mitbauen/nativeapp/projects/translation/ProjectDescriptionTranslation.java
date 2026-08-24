package io.github.absketches.mitbauen.nativeapp.projects.translation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record ProjectDescriptionTranslation(
    String sourceLanguage,
    String targetLanguage,
    String sourceTextHash,
    String translatedText,
    String provider,
    String model
) {

    public static String sourceTextHash(final String sourceText) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(sourceText.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hash = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hash.append("%02x".formatted(value & 0xff));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
