package io.github.absketches.mitbauen.nativeapp.auth;

public record UserProfile(
    String displayName,
    String bio,
    String email,
    boolean emailPublic
) {
}
