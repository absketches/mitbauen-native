package io.github.absketches.mitbauen.nativeapp.auth;

public record InviteLink(
    long id,
    String token,
    String allowedEmail,
    boolean active
) {
}
