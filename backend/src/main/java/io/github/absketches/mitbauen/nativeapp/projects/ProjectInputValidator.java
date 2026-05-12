package io.github.absketches.mitbauen.nativeapp.projects;

import java.net.URI;
import java.util.Optional;

public class ProjectInputValidator {

    private ProjectInputValidator() {
    }

    public static Optional<String> validate(final ProjectInput input) {
        if (outside(input.title(), ProjectFeedUtil.TITLE_MIN_LENGTH, ProjectFeedUtil.TITLE_MAX_LENGTH)) {
            return Optional.of(ProjectFeedUtil.PROJECT_TITLE_INVALID_CODE);
        }
        if (outside(input.description(), ProjectFeedUtil.DESCRIPTION_MIN_LENGTH, ProjectFeedUtil.DESCRIPTION_MAX_LENGTH)) {
            return Optional.of(ProjectFeedUtil.PROJECT_DESCRIPTION_INVALID_CODE);
        }
        if (outside(input.founderRole(), ProjectFeedUtil.FOUNDER_ROLE_MIN_LENGTH, ProjectFeedUtil.FOUNDER_ROLE_MAX_LENGTH)) {
            return Optional.of(ProjectFeedUtil.PROJECT_FOUNDER_ROLE_INVALID_CODE);
        }
        if (outside(input.founderCommitment(), ProjectFeedUtil.FOUNDER_COMMITMENT_MIN_LENGTH, ProjectFeedUtil.FOUNDER_COMMITMENT_MAX_LENGTH)) {
            return Optional.of(ProjectFeedUtil.PROJECT_FOUNDER_COMMITMENT_INVALID_CODE);
        }
        if (input.openRoles().size() < ProjectFeedUtil.OPEN_ROLES_MIN_COUNT) {
            return Optional.of(ProjectFeedUtil.PROJECT_OPEN_ROLES_MIN_CODE);
        }
        if (input.openRoles().size() > ProjectFeedUtil.OPEN_ROLES_MAX_COUNT) {
            return Optional.of(ProjectFeedUtil.PROJECT_OPEN_ROLES_MAX_CODE);
        }
        if (input.openRoles().stream().anyMatch(role ->
            outside(role.title(), ProjectFeedUtil.OPEN_ROLE_TITLE_MIN_LENGTH, ProjectFeedUtil.OPEN_ROLE_TITLE_MAX_LENGTH)
        )) {
            return Optional.of(ProjectFeedUtil.PROJECT_OPEN_ROLE_TITLE_INVALID_CODE);
        }
        if (input.openRoles().stream().anyMatch(role ->
            outside(role.commitment(), ProjectFeedUtil.OPEN_ROLE_COMMITMENT_MIN_LENGTH, ProjectFeedUtil.OPEN_ROLE_COMMITMENT_MAX_LENGTH)
        )) {
            return Optional.of(ProjectFeedUtil.PROJECT_OPEN_ROLE_COMMITMENT_INVALID_CODE);
        }
        if (input.links().size() > ProjectFeedUtil.PROJECT_LINKS_MAX_COUNT) {
            return Optional.of(ProjectFeedUtil.PROJECT_LINKS_MAX_CODE);
        }
        if (input.links().stream().anyMatch(link ->
            outside(link.label(), ProjectFeedUtil.PROJECT_LINK_LABEL_MIN_LENGTH, ProjectFeedUtil.PROJECT_LINK_LABEL_MAX_LENGTH)
        )) {
            return Optional.of(ProjectFeedUtil.PROJECT_LINK_LABEL_INVALID_CODE);
        }
        if (input.links().stream().anyMatch(link -> !validHttpUrl(link.url()))) {
            return Optional.of(ProjectFeedUtil.PROJECT_LINK_URL_INVALID_CODE);
        }
        return Optional.empty();
    }

    private static boolean outside(final String value, final int minimum, final int maximum) {
        return value == null || value.length() < minimum || value.length() > maximum;
    }

    private static boolean validHttpUrl(final String value) {
        if (value == null || value.length() > ProjectFeedUtil.PROJECT_LINK_URL_MAX_LENGTH) {
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
