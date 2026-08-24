package io.github.absketches.mitbauen.nativeapp.auth.model;

public record UserProfile(
    String displayName,
    String bio,
    String email,
    boolean emailPublic,
    boolean emailVerified
) {
}
