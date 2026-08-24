package io.github.absketches.mitbauen.nativeapp.projects;

import io.github.absketches.mitbauen.nativeapp.projects.model.ProjectInput;

import java.net.URI;
import java.util.Optional;

public class ProjectInputValidator {

    public static final int TITLE_MIN_LENGTH = 5;
    public static final int TITLE_MAX_LENGTH = 120;
    public static final int DESCRIPTION_MIN_LENGTH = 40;
    public static final int DESCRIPTION_MAX_LENGTH = 3000;
    public static final int FOUNDER_ROLE_MIN_LENGTH = 3;
    public static final int FOUNDER_ROLE_MAX_LENGTH = 120;
    public static final int FOUNDER_COMMITMENT_MIN_LENGTH = 5;
    public static final int FOUNDER_COMMITMENT_MAX_LENGTH = 500;
    public static final int OPEN_ROLE_TITLE_MIN_LENGTH = 3;
    public static final int OPEN_ROLE_TITLE_MAX_LENGTH = 120;
    public static final int OPEN_ROLE_COMMITMENT_MIN_LENGTH = 3;
    public static final int OPEN_ROLE_COMMITMENT_MAX_LENGTH = 500;
    public static final int OPEN_ROLES_MIN_COUNT = 1;
    public static final int OPEN_ROLES_MAX_COUNT = 6;
    public static final int PROJECT_LINKS_MAX_COUNT = 8;
    public static final int PROJECT_LINK_LABEL_MIN_LENGTH = 2;
    public static final int PROJECT_LINK_LABEL_MAX_LENGTH = 40;
    public static final int PROJECT_LINK_URL_MAX_LENGTH = 2048;
    public static final String PROJECT_TITLE_INVALID_CODE = "PROJECT_TITLE_INVALID";
    public static final String PROJECT_DESCRIPTION_INVALID_CODE = "PROJECT_DESCRIPTION_INVALID";
    public static final String PROJECT_FOUNDER_ROLE_INVALID_CODE = "PROJECT_FOUNDER_ROLE_INVALID";
    public static final String PROJECT_FOUNDER_COMMITMENT_INVALID_CODE = "PROJECT_FOUNDER_COMMITMENT_INVALID";
    public static final String PROJECT_OPEN_ROLES_MIN_CODE = "PROJECT_OPEN_ROLES_MIN";
    public static final String PROJECT_OPEN_ROLES_MAX_CODE = "PROJECT_OPEN_ROLES_MAX";
    public static final String PROJECT_OPEN_ROLE_TITLE_INVALID_CODE = "PROJECT_OPEN_ROLE_TITLE_INVALID";
    public static final String PROJECT_OPEN_ROLE_COMMITMENT_INVALID_CODE = "PROJECT_OPEN_ROLE_COMMITMENT_INVALID";
    public static final String PROJECT_LINKS_MAX_CODE = "PROJECT_LINKS_MAX";
    public static final String PROJECT_LINK_LABEL_INVALID_CODE = "PROJECT_LINK_LABEL_INVALID";
    public static final String PROJECT_LINK_URL_INVALID_CODE = "PROJECT_LINK_URL_INVALID";
    public static final String PROJECT_PAYLOAD_INVALID_CODE = "PROJECT_PAYLOAD_INVALID";

    private ProjectInputValidator() {
    }

    public static Optional<String> validate(final ProjectInput input) {
        if (outside(input.title(), TITLE_MIN_LENGTH, TITLE_MAX_LENGTH)) {
            return Optional.of(PROJECT_TITLE_INVALID_CODE);
        }
        if (input.descriptions().de() == null && input.descriptions().en() == null) {
            return Optional.of(PROJECT_DESCRIPTION_INVALID_CODE);
        }
        if (input.descriptions().de() != null
            && outside(input.descriptions().de(), DESCRIPTION_MIN_LENGTH, DESCRIPTION_MAX_LENGTH)) {
            return Optional.of(PROJECT_DESCRIPTION_INVALID_CODE);
        }
        if (input.descriptions().en() != null
            && outside(input.descriptions().en(), DESCRIPTION_MIN_LENGTH, DESCRIPTION_MAX_LENGTH)) {
            return Optional.of(PROJECT_DESCRIPTION_INVALID_CODE);
        }
        if (outside(input.founderRole(), FOUNDER_ROLE_MIN_LENGTH, FOUNDER_ROLE_MAX_LENGTH)) {
            return Optional.of(PROJECT_FOUNDER_ROLE_INVALID_CODE);
        }
        if (outside(input.founderCommitment(), FOUNDER_COMMITMENT_MIN_LENGTH, FOUNDER_COMMITMENT_MAX_LENGTH)) {
            return Optional.of(PROJECT_FOUNDER_COMMITMENT_INVALID_CODE);
        }
        if (input.openRoles().size() < OPEN_ROLES_MIN_COUNT) {
            return Optional.of(PROJECT_OPEN_ROLES_MIN_CODE);
        }
        if (input.openRoles().size() > OPEN_ROLES_MAX_COUNT) {
            return Optional.of(PROJECT_OPEN_ROLES_MAX_CODE);
        }
        if (input.openRoles().stream().anyMatch(role ->
            outside(role.title(), OPEN_ROLE_TITLE_MIN_LENGTH, OPEN_ROLE_TITLE_MAX_LENGTH)
        )) {
            return Optional.of(PROJECT_OPEN_ROLE_TITLE_INVALID_CODE);
        }
        if (input.openRoles().stream().anyMatch(role ->
            outside(role.commitment(), OPEN_ROLE_COMMITMENT_MIN_LENGTH, OPEN_ROLE_COMMITMENT_MAX_LENGTH)
        )) {
            return Optional.of(PROJECT_OPEN_ROLE_COMMITMENT_INVALID_CODE);
        }
        if (input.links().size() > PROJECT_LINKS_MAX_COUNT) {
            return Optional.of(PROJECT_LINKS_MAX_CODE);
        }
        if (input.links().stream().anyMatch(link ->
            outside(link.label(), PROJECT_LINK_LABEL_MIN_LENGTH, PROJECT_LINK_LABEL_MAX_LENGTH)
        )) {
            return Optional.of(PROJECT_LINK_LABEL_INVALID_CODE);
        }
        if (input.links().stream().anyMatch(link -> !validHttpUrl(link.url()))) {
            return Optional.of(PROJECT_LINK_URL_INVALID_CODE);
        }
        return Optional.empty();
    }

    private static boolean outside(final String value, final int minimum, final int maximum) {
        return value == null || value.length() < minimum || value.length() > maximum;
    }

    private static boolean validHttpUrl(final String value) {
        if (value == null || value.length() > PROJECT_LINK_URL_MAX_LENGTH) {
            return false;
        }
        try {
            final URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null
                && !uri.getHost().isBlank();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
