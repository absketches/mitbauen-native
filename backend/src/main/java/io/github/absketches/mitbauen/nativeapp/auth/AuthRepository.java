package io.github.absketches.mitbauen.nativeapp.auth;

import io.github.absketches.mitbauen.nativeapp.db.SqlTransactions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class AuthRepository {

    private static final String INVITE_LOOKUP_SQL = """
        select id, is_active
        from invite_links
        where token_hash = ?
        """;

    private static final String LOGIN_LOOKUP_SQL = """
        select
            u.id,
            u.display_name,
            u.email,
            u.email_verified_at is not null as email_verified,
            pc.password_hash
        from users u
        join password_credentials pc on pc.user_id = u.id
        where u.email = ? and u.is_deleted = false
        """;

    private static final String PASSWORD_RESET_RECIPIENT_LOOKUP_SQL = """
        select id, display_name, email
        from users
        where email = ? and is_deleted = false
        """;

    private static final String SESSION_LOOKUP_SQL = """
        select
            s.id,
            u.id as user_id,
            u.display_name,
            u.email,
            u.email_verified_at is not null as email_verified
        from sessions s
        join users u on u.id = s.user_id
        where s.token_hash = ? and s.expires_at > ? and u.is_deleted = false
        """;

    private static final String PROFILE_LOOKUP_SQL = """
        select
            id,
            display_name,
            bio,
            email,
            is_email_public,
            email_verified_at is not null as email_verified
        from users
        where id = ? and is_deleted = false
        """;

    private static final String PUBLIC_PROFILE_LOOKUP_SQL = """
        select
            display_name,
            bio,
            case when is_email_public then email end as email
        from users
        where public_id = ? and is_deleted = false
        """;

    private AuthRepository() {
    }

    public static Optional<InviteLink> findInviteByToken(final DataSource dataSource, final String token) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INVITE_LOOKUP_SQL)) {
            statement.setString(1, AuthUtil.hashToken(token));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new InviteLink(
                        resultSet.getLong("id"),
                        resultSet.getBoolean("is_active")
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load invite link", exception);
        }
    }

    public static Optional<SessionUser> createUserFromInvite(
        final DataSource dataSource,
        final InviteLink invite,
        final String normalizedEmail,
        final String displayName,
        final String passwordHash,
        final String bio,
        final boolean emailPublic,
        final boolean markEmailVerified
    ) {
        return SqlTransactions.execute(
            dataSource,
            "Unable to register user",
            connection -> {
                final long userId = insertUser(connection, normalizedEmail, displayName, bio, emailPublic, markEmailVerified);
                insertPasswordCredential(connection, userId, passwordHash);
                insertInviteRedemption(connection, invite.id(), userId, normalizedEmail);
                incrementInviteUseCount(connection, invite.id());
                return Optional.of(new SessionUser(userId, displayName, normalizedEmail, markEmailVerified));
            },
            (connection, exception) -> {
                if (isDuplicateEmail(connection, normalizedEmail, exception)) {
                    return Optional.empty();
                }
                throw exception;
            }
        );
    }

    public static Optional<LoginIdentity> findLoginIdentityByEmail(final DataSource dataSource, final String normalizedEmail) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(LOGIN_LOOKUP_SQL)) {
            statement.setString(1, normalizedEmail);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final long userId = resultSet.getLong("id");
                    return Optional.of(new LoginIdentity(
                        userId,
                        resultSet.getString("display_name"),
                        resultSet.getString("email"),
                        resultSet.getBoolean("email_verified"),
                        resultSet.getString("password_hash")
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load user credentials", exception);
        }
    }

    public static void createSession(final DataSource dataSource, final long userId, final String tokenHash, final Instant expiresAt) {
        final String sql = """
            insert into sessions (user_id, token_hash, expires_at)
            values (?, ?, ?)
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, tokenHash);
            statement.setTimestamp(3, Timestamp.from(expiresAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create session", exception);
        }
    }

    public static Optional<SessionUser> findSessionUserByTokenHash(final DataSource dataSource, final String tokenHash) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SESSION_LOOKUP_SQL)) {
            statement.setString(1, tokenHash);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final long sessionId = resultSet.getLong("id");
                    final long userId = resultSet.getLong("user_id");
                    final SessionUser sessionUser = new SessionUser(
                        userId,
                        resultSet.getString("display_name"),
                        resultSet.getString("email"),
                        resultSet.getBoolean("email_verified")
                    );
                    touchSession(connection, sessionId);
                    return Optional.of(sessionUser);
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load session", exception);
        }
    }

    public static void deleteSession(final DataSource dataSource, final String tokenHash) {
        final String sql = """
            delete from sessions
            where token_hash = ?
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete session", exception);
        }
    }

    public static void deleteAccount(final DataSource dataSource, final long userId) {
        SqlTransactions.execute(
            dataSource,
            "Unable to delete account",
            connection -> {
                deleteSessionsForUser(connection, userId);
                deletePasswordCredential(connection, userId);
                deleteEmailVerificationTokens(connection, userId);
                deleteEmailVerificationSends(connection, userId);
                deletePasswordResetTokens(connection, userId);
                softDeleteUser(connection, userId);
                return null;
            }
        );
    }

    public static Optional<SessionUser> findPasswordResetRecipientByEmail(final DataSource dataSource, final String normalizedEmail) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PASSWORD_RESET_RECIPIENT_LOOKUP_SQL)) {
            statement.setString(1, normalizedEmail);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new SessionUser(
                        resultSet.getLong("id"),
                        resultSet.getString("display_name"),
                        resultSet.getString("email"),
                        false
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load password reset recipient", exception);
        }
    }

    public static void createPasswordResetToken(
        final DataSource dataSource,
        final long userId,
        final String tokenHash,
        final Instant expiresAt
    ) {
        SqlTransactions.execute(
            dataSource,
            "Unable to create password reset token",
            connection -> {
                lockUser(connection, userId);
                deletePasswordResetTokens(connection, userId);
                insertPasswordResetToken(connection, userId, tokenHash, expiresAt);
                return null;
            }
        );
    }

    public static boolean resetPassword(final DataSource dataSource, final String tokenHash, final String passwordHash) {
        final String sql = """
            select user_id
            from password_reset_tokens
            where token_hash = ? and expires_at > ?
            """;
        return SqlTransactions.execute(
            dataSource,
            "Unable to reset password",
            connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, tokenHash);
                    statement.setTimestamp(2, Timestamp.from(Instant.now()));
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return false;
                        }
                        final long userId = resultSet.getLong("user_id");
                        lockUser(connection, userId);
                        updatePasswordCredential(connection, userId, passwordHash);
                        deletePasswordResetTokens(connection, userId);
                        deleteSessionsForUser(connection, userId);
                        return true;
                    }
                }
            }
        );
    }

    public static Optional<UserProfile> findProfileByUserId(final DataSource dataSource, final long userId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PROFILE_LOOKUP_SQL)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new UserProfile(
                    resultSet.getString("display_name"),
                    resultSet.getString("bio"),
                    resultSet.getString("email"),
                    resultSet.getBoolean("is_email_public"),
                    resultSet.getBoolean("email_verified")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load user profile", exception);
        }
    }

    public static Optional<UserProfile> findPublicProfileByPublicId(final DataSource dataSource, final String publicId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PUBLIC_PROFILE_LOOKUP_SQL)) {
            statement.setString(1, publicId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new UserProfile(
                    resultSet.getString("display_name"),
                    resultSet.getString("bio"),
                    resultSet.getString("email"),
                    resultSet.getString("email") != null,
                    true
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load public user profile", exception);
        }
    }

    public static boolean isDeletedPublicProfile(final DataSource dataSource, final String publicId) {
        final String sql = """
            select is_deleted
            from users
            where public_id = ?
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, publicId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean("is_deleted");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load deleted public profile state", exception);
        }
    }

    public static UserProfile updateProfile(
        final DataSource dataSource,
        final long userId,
        final String displayName,
        final String bio,
        final boolean emailPublic
    ) {
        final String sql = """
            update users
            set display_name = ?, bio = ?, is_email_public = ?
            where id = ?
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, displayName);
            statement.setString(2, bio);
            statement.setBoolean(3, emailPublic);
            statement.setLong(4, userId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update user profile", exception);
        }

        return findProfileByUserId(dataSource, userId)
            .orElseThrow(() -> new IllegalStateException("Updated profile not found for user " + userId));
    }

    private static long insertUser(
        final Connection connection,
        final String normalizedEmail,
        final String displayName,
        final String bio,
        final boolean emailPublic,
        final boolean markEmailVerified
    ) throws SQLException {
        final String sql = """
            insert into users (public_id, display_name, email, bio, is_email_public, email_verified_at)
            values (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, AuthUtil.newPublicProfileId());
            statement.setString(2, displayName);
            statement.setString(3, normalizedEmail);
            statement.setString(4, bio);
            statement.setBoolean(5, emailPublic);
            statement.setTimestamp(6, markEmailVerified ? Timestamp.from(Instant.now()) : null);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
            throw new IllegalStateException("User insert did not return a generated id");
        }
    }

    private static void insertPasswordCredential(final Connection connection, final long userId, final String passwordHash) throws SQLException {
        final String sql = """
            insert into password_credentials (user_id, password_hash)
            values (?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, passwordHash);
            statement.executeUpdate();
        }
    }

    private static void insertInviteRedemption(
        final Connection connection,
        final long inviteLinkId,
        final long userId,
        final String normalizedEmail
    ) throws SQLException {
        final String sql = """
            insert into invite_redemptions (invite_link_id, used_by_user_id, used_email)
            values (?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, inviteLinkId);
            statement.setLong(2, userId);
            statement.setString(3, normalizedEmail);
            statement.executeUpdate();
        }
    }

    private static void incrementInviteUseCount(final Connection connection, final long inviteLinkId) throws SQLException {
        final String sql = """
            update invite_links
            set use_count = use_count + 1
            where id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, inviteLinkId);
            statement.executeUpdate();
        }
    }

    private static void touchSession(final Connection connection, final long sessionId) throws SQLException {
        final String sql = """
            update sessions
            set last_seen_at = ?
            where id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setLong(2, sessionId);
            statement.executeUpdate();
        }
    }

    private static void deleteSessionsForUser(final Connection connection, final long userId) throws SQLException {
        final String sql = """
            delete from sessions
            where user_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    private static void deletePasswordCredential(final Connection connection, final long userId) throws SQLException {
        final String sql = """
            delete from password_credentials
            where user_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    private static void deleteEmailVerificationSends(final Connection connection, final long userId) throws SQLException {
        final String sql = """
            delete from email_verification_sends
            where user_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    private static void insertPasswordResetToken(
        final Connection connection,
        final long userId,
        final String tokenHash,
        final Instant expiresAt
    ) throws SQLException {
        final String sql = """
            insert into password_reset_tokens (user_id, token_hash, expires_at)
            values (?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, tokenHash);
            statement.setTimestamp(3, Timestamp.from(expiresAt));
            statement.executeUpdate();
        }
    }

    private static void updatePasswordCredential(
        final Connection connection,
        final long userId,
        final String passwordHash
    ) throws SQLException {
        final String sql = """
            update password_credentials
            set password_hash = ?, updated_at = current_timestamp
            where user_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setLong(2, userId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Password credential not found for reset " + userId);
            }
        }
    }

    private static void deletePasswordResetTokens(final Connection connection, final long userId) throws SQLException {
        final String sql = """
            delete from password_reset_tokens
            where user_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    private static void softDeleteUser(final Connection connection, final long userId) throws SQLException {
        final String sql = """
            update users
            set
                email = ?,
                bio = '',
                is_email_public = false,
                email_verified_at = null,
                is_deleted = true,
                deleted_at = current_timestamp
            where id = ? and is_deleted = false
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "deleted-user-" + userId + "@deleted.mitbauen.local");
            statement.setLong(2, userId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("User not found for account deletion " + userId);
            }
        }
    }

    private static Optional<Long> userIdByEmail(final Connection connection, final String normalizedEmail) throws SQLException {
        final String sql = """
            select id
            from users
            where email = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedEmail);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getLong("id"));
                }
                return Optional.empty();
            }
        }
    }

    private static boolean isDuplicateEmail(
        final Connection connection,
        final String normalizedEmail,
        final SQLException exception
    ) throws SQLException {
        return isUniqueViolation(exception) && userIdByEmail(connection, normalizedEmail).isPresent();
    }

    private static boolean isUniqueViolation(final SQLException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean beginEmailVerificationSendAttempt(
        final DataSource dataSource,
        final long userId,
        final String tokenHash,
        final Instant expiresAt,
        final Instant sendWindowStart,
        final int maxEmailsPer24Hours
    ) {
        return SqlTransactions.execute(
            dataSource,
            "Unable to begin email verification send attempt",
            connection -> {
                lockUser(connection, userId);
                if (countEmailVerificationSendsSince(connection, userId, sendWindowStart) >= maxEmailsPer24Hours) {
                    return false;
                }
                insertEmailVerificationToken(connection, userId, tokenHash, expiresAt);
                insertEmailVerificationSendRecord(connection, userId, tokenHash);
                return true;
            }
        );
    }

    public static void completeEmailVerificationSendAttempt(
        final DataSource dataSource,
        final long userId,
        final String tokenHash
    ) {
        SqlTransactions.execute(
            dataSource,
            "Unable to complete email verification send attempt",
            connection -> {
                lockUser(connection, userId);
                deleteEmailVerificationTokensExcept(connection, userId, tokenHash);
                return null;
            }
        );
    }

    public static void abortEmailVerificationSendAttempt(
        final DataSource dataSource,
        final String tokenHash
    ) {
        SqlTransactions.execute(
            dataSource,
            "Unable to abort email verification send attempt",
            connection -> {
                deleteEmailVerificationSendRecordByTokenHash(connection, tokenHash);
                deleteEmailVerificationTokenByHash(connection, tokenHash);
                return null;
            }
        );
    }

    public static boolean confirmEmailVerification(final DataSource dataSource, final String tokenHash) {
        final String sql = """
            select user_id
            from email_verification_tokens
            where token_hash = ? and expires_at > ?
            """;
        return SqlTransactions.execute(
            dataSource,
            "Unable to confirm email verification",
            connection -> {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, tokenHash);
                    statement.setTimestamp(2, Timestamp.from(Instant.now()));
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return false;
                        }
                        final long userId = resultSet.getLong("user_id");
                        markEmailVerified(connection, userId);
                        deleteEmailVerificationTokens(connection, userId);
                        return true;
                    }
                }
            }
        );
    }

    private static void insertEmailVerificationToken(
        final Connection connection,
        final long userId,
        final String tokenHash,
        final Instant expiresAt
    ) throws SQLException {
        final String sql = """
            insert into email_verification_tokens (user_id, token_hash, expires_at)
            values (?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, tokenHash);
            statement.setTimestamp(3, Timestamp.from(expiresAt));
            statement.executeUpdate();
        }
    }

    private static void insertEmailVerificationSendRecord(
        final Connection connection,
        final long userId,
        final String tokenHash
    ) throws SQLException {
        final String sql = """
            insert into email_verification_sends (user_id, token_hash)
            values (?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, tokenHash);
            statement.executeUpdate();
        }
    }

    private static long countEmailVerificationSendsSince(
        final Connection connection,
        final long userId,
        final Instant sendWindowStart
    ) throws SQLException {
        final String sql = """
            select count(*)
            from email_verification_sends
            where user_id = ? and created_at >= ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setTimestamp(2, Timestamp.from(sendWindowStart));
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static void deleteEmailVerificationTokens(final Connection connection, final long userId) throws SQLException {
        final String sql = """
            delete from email_verification_tokens
            where user_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    private static void deleteEmailVerificationTokensExcept(
        final Connection connection,
        final long userId,
        final String tokenHash
    ) throws SQLException {
        final String sql = """
            delete from email_verification_tokens
            where user_id = ? and token_hash <> ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, tokenHash);
            statement.executeUpdate();
        }
    }

    private static void deleteEmailVerificationTokenByHash(final Connection connection, final String tokenHash) throws SQLException {
        final String sql = """
            delete from email_verification_tokens
            where token_hash = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            statement.executeUpdate();
        }
    }

    private static void deleteEmailVerificationSendRecordByTokenHash(final Connection connection, final String tokenHash) throws SQLException {
        final String sql = """
            delete from email_verification_sends
            where token_hash = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            statement.executeUpdate();
        }
    }

    private static void lockUser(final Connection connection, final long userId) throws SQLException {
        final String sql = """
            select id
            from users
            where id = ?
            for update
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("User not found for locked auth operation " + userId);
                }
            }
        }
    }

    private static void markEmailVerified(final Connection connection, final long userId) throws SQLException {
        final String sql = """
            update users
            set email_verified_at = current_timestamp
            where id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

}
