package io.github.absketches.mitbauen.nativeapp.projects;

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

public class ProjectFeedRepository {

    private static final String PROJECT_FEED_SQL = """
        select
            p.id,
            p.slug,
            p.title,
            p.summary,
            p.status,
            p.created_at,
            u.display_name as founder_name,
            founder_role.title as founder_role_title,
            founder_role.commitment as founder_commitment
        from projects p
        join users u on u.id = p.owner_user_id
        join project_roles founder_role on founder_role.project_id = p.id and founder_role.is_founder = true
        order by
            case p.status
                when 'active' then 1
                when 'completed' then 2
                else 3
            end,
            p.created_at desc,
            p.id desc
        """;

    private final DataSource dataSource;

    public ProjectFeedRepository(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<ProjectCard> listProjects() {
        final List<ProjectCard> projects = new ArrayList<>();
        final List<Long> projectIds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PROJECT_FEED_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                final long projectId = resultSet.getLong("id");
                projectIds.add(projectId);
                projects.add(new ProjectCard(
                    projectId,
                    resultSet.getString("slug"),
                    resultSet.getString("title"),
                    resultSet.getString("summary"),
                    resultSet.getString("status"),
                    new FounderInfo(
                        resultSet.getString("founder_name"),
                        resultSet.getString("founder_role_title"),
                        resultSet.getString("founder_commitment")
                    ),
                    List.of(),
                    resultSet.getTimestamp("created_at").toInstant()
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load project feed", exception);
        }

        if (projects.isEmpty()) {
            return List.of();
        }

        final Map<Long, List<OpenRole>> openRolesByProjectId = loadOpenRoles(projectIds);
        return projects.stream()
            .map(project -> new ProjectCard(
                project.id(),
                project.slug(),
                project.title(),
                project.summary(),
                project.status(),
                project.founder(),
                openRolesByProjectId.getOrDefault(project.id(), List.of()),
                project.createdAt()
            ))
            .toList();
    }

    private Map<Long, List<OpenRole>> loadOpenRoles(final List<Long> projectIds) {
        final StringJoiner placeholders = new StringJoiner(", ");
        projectIds.forEach(projectId -> placeholders.add("?"));

        final String sql = """
            select project_id, title, commitment
            from project_roles
            where is_open = true and is_founder = false and project_id in (%s)
            order by project_id, sort_order asc, id asc
            """.formatted(placeholders);

        final Map<Long, List<OpenRole>> roles = new HashMap<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < projectIds.size(); index++) {
                statement.setLong(index + 1, projectIds.get(index));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    roles.computeIfAbsent(resultSet.getLong("project_id"), ignored -> new ArrayList<>())
                        .add(new OpenRole(
                            resultSet.getString("title"),
                            resultSet.getString("commitment")
                        ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load project roles", exception);
        }

        return roles;
    }
}
