package io.github.absketches.mitbauen.nativeapp.comments;

import io.github.absketches.mitbauen.nativeapp.db.SqlTransactions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProjectCommentsRepository {

    private static final int PROJECT_COMMENTS_LIMIT = 200;

    private ProjectCommentsRepository() {
    }

    public static List<ProjectComment> listComments(final DataSource dataSource, final long projectId) {
        final String sql = """
            select id, body, created_at, author_public_id, author_display_name
            from (
            select
                c.id,
                c.body,
                c.created_at,
                u.public_id as author_public_id,
                u.display_name as author_display_name
            from project_comments c
            join users u on u.id = c.user_id
            where c.project_id = ?
            order by c.created_at desc, c.id desc
            limit ?
            ) recent_comments
            order by created_at asc, id asc
            """;
        final List<ProjectComment> comments = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.setInt(2, PROJECT_COMMENTS_LIMIT);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    comments.add(commentFrom(resultSet));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load project comments", exception);
        }
        return comments;
    }

    public static ProjectComment createComment(
        final DataSource dataSource,
        final long projectId,
        final long userId,
        final String body
    ) {
        return SqlTransactions.execute(
            dataSource,
            "Unable to create project comment",
            connection -> {
                final long commentId = insertComment(connection, projectId, userId, body);
                return findCommentById(connection, commentId)
                    .orElseThrow(() -> new IllegalStateException("Inserted comment not found " + commentId));
            }
        );
    }

    public static void markCommentsRead(final DataSource dataSource, final long projectId, final long userId) {
        final String sql = """
            insert into project_comment_reads (project_id, user_id, last_read_at)
            select ?, ?, coalesce(max(created_at), current_timestamp)
            from project_comments
            where project_id = ?
            on conflict (project_id, user_id)
            do update set last_read_at = excluded.last_read_at
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.setLong(2, userId);
            statement.setLong(3, projectId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to mark project comments read", exception);
        }
    }

    private static long insertComment(
        final Connection connection,
        final long projectId,
        final long userId,
        final String body
    ) throws SQLException {
        final String sql = """
            insert into project_comments (project_id, user_id, body)
            values (?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, projectId);
            statement.setLong(2, userId);
            statement.setString(3, body);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
            throw new IllegalStateException("Comment insert did not return a generated id");
        }
    }

    private static Optional<ProjectComment> findCommentById(final Connection connection, final long commentId) throws SQLException {
        final String sql = """
            select
                c.id,
                c.body,
                c.created_at,
                u.public_id as author_public_id,
                u.display_name as author_display_name
            from project_comments c
            join users u on u.id = c.user_id
            where c.id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, commentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(commentFrom(resultSet)) : Optional.empty();
            }
        }
    }

    private static ProjectComment commentFrom(final ResultSet resultSet) throws SQLException {
        return new ProjectComment(
            resultSet.getLong("id"),
            resultSet.getString("body"),
            resultSet.getString("author_public_id"),
            resultSet.getString("author_display_name"),
            resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
