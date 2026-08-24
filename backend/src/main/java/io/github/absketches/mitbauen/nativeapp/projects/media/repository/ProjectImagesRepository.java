package io.github.absketches.mitbauen.nativeapp.projects.media.repository;

import io.github.absketches.mitbauen.nativeapp.projects.media.model.ProjectImage;
import io.github.absketches.mitbauen.nativeapp.projects.media.model.ProjectImageContent;

import io.github.absketches.mitbauen.nativeapp.db.SqlTransactions;
import io.github.absketches.mitbauen.nativeapp.db.SqlUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProjectImagesRepository {

    private ProjectImagesRepository() {
    }

    public static Map<Long, List<ProjectImage>> loadProjectImages(final DataSource dataSource, final List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }

        final String sql = """
            select id, project_id, content_type, size_bytes, alt_text, created_at
            from project_images
            where project_id in (%s)
            order by project_id, sort_order asc, id asc
            """.formatted(SqlUtil.placeholders(projectIds.size()));

        final Map<Long, List<ProjectImage>> images = new HashMap<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            SqlUtil.bindLongs(statement, projectIds);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    images.computeIfAbsent(resultSet.getLong("project_id"), ignored -> new ArrayList<>())
                        .add(new ProjectImage(
                            resultSet.getLong("id"),
                            resultSet.getString("content_type"),
                            resultSet.getInt("size_bytes"),
                            resultSet.getString("alt_text"),
                            resultSet.getTimestamp("created_at").toInstant()
                        ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load project images", exception);
        }

        return images;
    }

    public static Optional<ProjectImage> createProjectImage(
        final DataSource dataSource,
        final long projectId,
        final String contentType,
        final byte[] data,
        final String altText,
        final int maxImages
    ) {
        return SqlTransactions.execute(
            dataSource,
            "Unable to save project image",
            connection -> {
                lockProject(connection, projectId);
                if (countProjectImages(connection, projectId) >= maxImages) {
                    return Optional.empty();
                }
                final int sortOrder = nextProjectImageSortOrder(connection, projectId);
                return Optional.of(insertProjectImage(connection, projectId, contentType, data, altText, sortOrder));
            }
        );
    }

    public static Optional<ProjectImageContent> findProjectImageContentBySlug(final DataSource dataSource, final String slug, final long imageId) {
        final String sql = """
            select image.id, image.project_id, image.content_type, image.image_data
            from project_images image
            join projects project on project.id = image.project_id
            where project.slug = ? and image.id = ?
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, slug);
            statement.setLong(2, imageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ProjectImageContent(
                    resultSet.getLong("id"),
                    resultSet.getLong("project_id"),
                    resultSet.getString("content_type"),
                    resultSet.getBytes("image_data")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load project image", exception);
        }
    }

    public static boolean deleteProjectImage(final DataSource dataSource, final long projectId, final long imageId) {
        final String sql = """
            delete from project_images
            where project_id = ? and id = ?
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.setLong(2, imageId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete project image", exception);
        }
    }

    private static void lockProject(final Connection connection, final long projectId) throws SQLException {
        final String sql = """
            select id
            from projects
            where id = ?
            for update
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Project not found for image update " + projectId);
                }
            }
        }
    }

    private static int countProjectImages(final Connection connection, final long projectId) throws SQLException {
        final String sql = """
            select count(*)
            from project_images
            where project_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private static ProjectImage insertProjectImage(
        final Connection connection,
        final long projectId,
        final String contentType,
        final byte[] data,
        final String altText,
        final int sortOrder
    ) throws SQLException {
        final String sql = """
            insert into project_images (project_id, content_type, size_bytes, image_data, alt_text, sort_order, created_at)
            values (?, ?, ?, ?, ?, ?, ?)
            returning id, content_type, size_bytes, alt_text, created_at
            """;
        final Instant now = Instant.now();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.setString(2, contentType);
            statement.setInt(3, data.length);
            statement.setBytes(4, data);
            statement.setString(5, altText == null ? "" : altText);
            statement.setInt(6, sortOrder);
            statement.setTimestamp(7, Timestamp.from(now));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Project image insert did not return a generated id");
                }
                return new ProjectImage(
                    resultSet.getLong("id"),
                    resultSet.getString("content_type"),
                    resultSet.getInt("size_bytes"),
                    resultSet.getString("alt_text"),
                    resultSet.getTimestamp("created_at").toInstant()
                );
            }
        }
    }

    private static int nextProjectImageSortOrder(final Connection connection, final long projectId) throws SQLException {
        final String sql = """
            select coalesce(max(sort_order), 0) + 1
            from project_images
            where project_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 1;
            }
        }
    }
}
