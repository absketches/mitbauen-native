package io.github.absketches.mitbauen.nativeapp.projects.media;

import io.github.absketches.mitbauen.nativeapp.projects.ProjectFeedUtil;

import java.util.Optional;

public class ProjectImageValidator {

    private ProjectImageValidator() {
    }

    public static Optional<String> validateUpload(final String contentType, final byte[] body, final String altText) {
        if (!validProjectImageContentType(contentType)) {
            return Optional.of(ProjectFeedUtil.PROJECT_IMAGE_TYPE_INVALID_CODE);
        }
        if (body == null || body.length == 0 || body.length > ProjectFeedUtil.PROJECT_IMAGE_MAX_BYTES) {
            return Optional.of(ProjectFeedUtil.PROJECT_IMAGE_TOO_LARGE_CODE);
        }
        if (!validProjectImageBytes(contentType, body)) {
            return Optional.of(ProjectFeedUtil.PROJECT_IMAGE_TYPE_INVALID_CODE);
        }
        if (altText != null && altText.length() > ProjectFeedUtil.PROJECT_IMAGE_ALT_TEXT_MAX_LENGTH) {
            return Optional.of(ProjectFeedUtil.PROJECT_IMAGE_ALT_TEXT_INVALID_CODE);
        }
        return Optional.empty();
    }

    public static String canonicalContentType(final String contentType) {
        if (contentType == null) {
            return "";
        }
        final int parametersStart = contentType.indexOf(';');
        final String mediaType = parametersStart >= 0 ? contentType.substring(0, parametersStart) : contentType;
        return mediaType.trim().toLowerCase();
    }

    private static boolean validProjectImageContentType(final String contentType) {
        return switch (canonicalContentType(contentType)) {
            case "image/jpeg", "image/png", "image/webp" -> true;
            default -> false;
        };
    }

    private static boolean validProjectImageBytes(final String contentType, final byte[] body) {
        return switch (canonicalContentType(contentType)) {
            case "image/jpeg" -> hasPrefix(body, 0xFF, 0xD8, 0xFF);
            case "image/png" -> hasPrefix(body, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/webp" -> body.length >= 12
                && hasPrefix(body, 0x52, 0x49, 0x46, 0x46)
                && body[8] == 0x57
                && body[9] == 0x45
                && body[10] == 0x42
                && body[11] == 0x50;
            default -> false;
        };
    }

    private static boolean hasPrefix(final byte[] body, final int... prefix) {
        if (body.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((body[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
