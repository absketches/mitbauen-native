package io.github.absketches.mitbauen.nativeapp.auth;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class AuthRepository {

    private static final String INVITE_LOOKUP_SQL = """
        select id, allowed_email, is_active
        from invite_links
        where token_hash = ?
        """;

    private static final String EMAIL_EXISTS_SQL = """
        select 1
        from users
        where email = ?
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
                        resultSet.getString("allowed_email"),
                        resultSet.getBoolean("is_active")
                    ));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load invite link", exception);
        }
    }

    public static boolean emailExists(final DataSource dataSource, final String normalizedEmail) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(EMAIL_EXISTS_SQL)) {
            statement.setString(1, normalizedEmail);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to check email", exception);
        }
    }

    public static SessionUser createUserFromInvite(
        final DataSource dataSource,
        final InviteLink invite,
        final String normalizedEmail,
        final String displayName,
        final String passwordHash,
        final String ownedInviteToken
    ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                final long userId = insertUser(connection, normalizedEmail, displayName);
                insertPasswordCredential(connection, userId, passwordHash);
                insertInviteRedemption(connection, invite.id(), userId, normalizedEmail);
                incrementInviteUseCount(connection, invite.id());
                insertOwnedInviteLink(connection, userId, ownedInviteToken);
                connection.commit();
                return new SessionUser(userId, displayName, normalizedEmail, ownedInviteToken);
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
                    return Optional.of(new LoginIdentity(
                        resultSet.getLong("id"),
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
                    final SessionUser sessionUser = new SessionUser(
                        resultSet.getLong("user_id"),
                        resultSet.getString("display_name"),
                        resultSet.getString("email"),
                        null
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

    private static long insertUser(final Connection connection, final String normalizedEmail, final String displayName) throws SQLException {
        final String sql = """
            insert into users (handle, display_name, email)
            values (?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nextHandle(connection, displayName, normalizedEmail));
            statement.setString(2, displayName);
            statement.setString(3, normalizedEmail);
            statement.executeUpdate();
            return userIdByEmail(connection, normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("User insert did not create a readable id"));
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

    private static void insertOwnedInviteLink(final Connection connection, final long userId, final String ownedInviteToken) throws SQLException {
        final String sql = """
            insert into invite_links (created_by_user_id, token_hash, is_active, use_count)
            values (?, ?, true, 0)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, AuthUtil.hashToken(ownedInviteToken));
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

    private static String nextHandle(final Connection connection, final String displayName, final String normalizedEmail) throws SQLException {
        final String baseHandle = AuthUtil.handleFrom(displayName, normalizedEmail);
        String handle = baseHandle;
        int suffix = 2;
        while (handleExists(connection, handle)) {
            handle = baseHandle + "-" + suffix;
            suffix++;
        }
        return handle;
    }

    private static boolean handleExists(final Connection connection, final String handle) throws SQLException {
        final String sql = """
            select 1
            from users
            where handle = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, handle);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
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
}
