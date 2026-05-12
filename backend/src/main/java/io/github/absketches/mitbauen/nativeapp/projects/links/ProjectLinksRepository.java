package io.github.absketches.mitbauen.nativeapp.projects.links;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class ProjectLinksRepository {

    private ProjectLinksRepository() {
    }

    public static Map<Long, List<ProjectLink>> loadProjectLinks(final DataSource dataSource, final List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }

        final StringJoiner placeholders = new StringJoiner(", ");
        projectIds.forEach(projectId -> placeholders.add("?"));

        final String sql = """
            select project_id, label, url
            from project_links
            where project_id in (%s)
            order by project_id, sort_order asc, id asc
            """.formatted(placeholders);

        final Map<Long, List<ProjectLink>> links = new HashMap<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < projectIds.size(); index++) {
                statement.setLong(index + 1, projectIds.get(index));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    links.computeIfAbsent(resultSet.getLong("project_id"), ignored -> new ArrayList<>())
                        .add(new ProjectLink(
                            resultSet.getString("label"),
                            resultSet.getString("url")
                        ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load project links", exception);
        }

        return links;
    }

    public static void replaceProjectLinks(final Connection connection, final long projectId, final List<ProjectLink> links) throws SQLException {
        deleteProjectLinks(connection, projectId);
        insertProjectLinks(connection, projectId, links);
    }

    public static void insertProjectLinks(final Connection connection, final long projectId, final List<ProjectLink> links) throws SQLException {
        final String sql = """
            insert into project_links (project_id, label, url, sort_order)
            values (?, ?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < links.size(); index++) {
                final ProjectLink link = links.get(index);
                statement.setLong(1, projectId);
                statement.setString(2, link.label());
                statement.setString(3, link.url());
                statement.setInt(4, index + 1);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void deleteProjectLinks(final Connection connection, final long projectId) throws SQLException {
        final String sql = """
            delete from project_links
            where project_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.executeUpdate();
        }
    }
}
