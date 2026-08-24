package io.github.absketches.mitbauen.nativeapp.auth.model;

public record SessionUser(
    long id,
    String displayName,
    String email,
    boolean emailVerified
) {
}
