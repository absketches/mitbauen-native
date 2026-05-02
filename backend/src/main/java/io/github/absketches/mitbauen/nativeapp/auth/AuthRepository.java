package io.github.absketches.mitbauen.nativeapp.auth;

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
            pc.password_hash
        from users u
        join password_credentials pc on pc.user_id = u.id
        where u.email = ?
        """;

    private static final String SESSION_LOOKUP_SQL = """
        select
            s.id,
            u.id as user_id,
            u.display_name,
            u.email
        from sessions s
        join users u on u.id = s.user_id
        where s.token_hash = ? and s.expires_at > ?
        """;

    private static final String PROFILE_LOOKUP_SQL = """
        select
            id,
            display_name,
            bio,
            email,
            is_email_public
        from users
        where id = ?
        """;

    private static final String PUBLIC_PROFILE_LOOKUP_SQL = """
        select
            display_name,
            bio,
            case when is_email_public then email end as email
        from users
        where public_id = ?
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
        final boolean emailPublic
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                final long userId = insertUser(connection, normalizedEmail, displayName, bio, emailPublic);
                insertPasswordCredential(connection, userId, passwordHash);
                insertInviteRedemption(connection, invite.id(), userId, normalizedEmail);
                incrementInviteUseCount(connection, invite.id());
                connection.commit();
                return Optional.of(new SessionUser(userId, displayName, normalizedEmail));
            } catch (SQLException exception) {
                connection.rollback();
                if (isDuplicateEmail(connection, normalizedEmail, exception)) {
                    return Optional.empty();
                }
                throw exception;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to register user", exception);
        }
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
                        resultSet.getString("email")
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
                    resultSet.getBoolean("is_email_public")
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
                    resultSet.getString("email") != null
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load public user profile", exception);
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
        final boolean emailPublic
    ) throws SQLException {
        final String sql = """
            insert into users (public_id, display_name, email, bio, is_email_public)
            values (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, AuthUtil.newPublicProfileId());
            statement.setString(2, displayName);
            statement.setString(3, normalizedEmail);
            statement.setString(4, bio);
            statement.setBoolean(5, emailPublic);
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

}
