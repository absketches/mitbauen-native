package io.github.absketches.mitbauen.nativeapp.auth;

public record LoginIdentity(
    long userId,
    String displayName,
    String email,
    String passwordHash,
    String inviteToken
) {
    public SessionUser toSessionUser() {
        return new SessionUser(userId, displayName, email, inviteToken);
    }
}
