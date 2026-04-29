package io.github.absketches.mitbauen.nativeapp.projects;

import java.util.List;

public record ProjectInput(
    String title,
    String description,
    String founderRole,
    String founderCommitment,
    List<OpenRole> openRoles
) {
}
