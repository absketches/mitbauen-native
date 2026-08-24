package io.github.absketches.mitbauen.nativeapp.auth;

import io.github.absketches.mitbauen.nativeapp.db.DatabaseRuntime;

public final class AuthServiceTestFactory {

    private AuthServiceTestFactory() {
    }

    public static AuthService authService(
        final DatabaseRuntime databaseRuntime,
        final EmailVerificationSettings emailVerificationSettings,
        final TransactionalEmailSender transactionalEmailSender
    ) {
        return new AuthService(databaseRuntime, emailVerificationSettings, transactionalEmailSender);
    }
}
