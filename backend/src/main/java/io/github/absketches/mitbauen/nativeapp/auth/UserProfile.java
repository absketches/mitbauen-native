package io.github.absketches.mitbauen.nativeapp.auth;

public record UserProfile(
    long id,
    String displayName,
    String bio,
    String email,
    boolean emailPublic
) {
}
