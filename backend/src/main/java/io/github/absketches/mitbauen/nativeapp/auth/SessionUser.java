package io.github.absketches.mitbauen.nativeapp.auth;

public record SessionUser(
    long id,
    String displayName,
    String email,
    boolean emailVerified
) {
}
