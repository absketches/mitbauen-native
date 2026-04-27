package io.github.absketches.mitbauen.nativeapp.auth;

public record InviteLink(
    long id,
    String allowedEmail,
    boolean active
) {
}
