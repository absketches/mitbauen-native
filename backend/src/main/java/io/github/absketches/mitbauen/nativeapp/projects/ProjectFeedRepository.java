package io.github.absketches.mitbauen.nativeapp.projects;

import io.github.absketches.mitbauen.nativeapp.db.SqlTransactions;
import io.github.absketches.mitbauen.nativeapp.db.SqlUtil;
import io.github.absketches.mitbauen.nativeapp.projects.links.ProjectLink;
import io.github.absketches.mitbauen.nativeapp.projects.links.ProjectLinksRepository;
import io.github.absketches.mitbauen.nativeapp.projects.media.ProjectImage;
import io.github.absketches.mitbauen.nativeapp.projects.media.ProjectImagesRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProjectFeedRepository {

    private static final int PROJECT_FEED_LIMIT = 100;

    private static final String PROJECT_FEED_SQL = """
        select
            p.id,
            p.slug,
            p.title,
            p.description_de,
            p.description_en,
            p.status,
            p.created_at,
            u.public_id as founder_public_id,
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
        limit ?
        """;

    private static final String PROJECT_DETAILS_SQL = """
        select
            p.id,
            p.owner_user_id,
            p.slug,
            p.title,
            p.description_de,
            p.description_en,
            p.status,
            p.created_at,
            p.updated_at,
            u.public_id as founder_public_id,
            u.display_name as founder_name,
            founder_role.title as founder_role_title,
            founder_role.commitment as founder_commitment
        from projects p
        join users u on u.id = p.owner_user_id
        join project_roles founder_role on founder_role.project_id = p.id and founder_role.is_founder = true
        where p.slug = ?
        """;

    private ProjectFeedRepository() {
    }

    public static List<ProjectCard> listProjects(final DataSource dataSource) {
        final List<ProjectCard> projects = new ArrayList<>();
        final List<Long> projectIds = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PROJECT_FEED_SQL)) {
            statement.setInt(1, PROJECT_FEED_LIMIT);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final long projectId = resultSet.getLong("id");
                    projectIds.add(projectId);
                    projects.add(new ProjectCard(
                        projectId,
                        resultSet.getString("slug"),
                        resultSet.getString("title"),
                        descriptionsFrom(resultSet),
                        resultSet.getString("status"),
                        new FounderInfo(
                            resultSet.getString("founder_public_id"),
                            resultSet.getString("founder_name"),
                            resultSet.getString("founder_role_title"),
                            resultSet.getString("founder_commitment")
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        resultSet.getTimestamp("created_at").toInstant()
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load project feed", exception);
        }

        if (projects.isEmpty()) {
            return List.of();
        }

        final Map<Long, List<OpenRole>> openRolesByProjectId = loadOpenRoles(dataSource, projectIds);
        final Map<Long, List<ProjectLink>> linksByProjectId = ProjectLinksRepository.loadProjectLinks(dataSource, projectIds);
        final Map<Long, List<ProjectImage>> imagesByProjectId = ProjectImagesRepository.loadProjectImages(dataSource, projectIds);
        final Map<Long, Map<String, ProjectDescriptionTranslation>> translationsByProjectId =
            ProjectDescriptionTranslationRepository.loadTranslations(dataSource, projectIds);
        return projects.stream()
            .map(project -> new ProjectCard(
                project.id(),
                project.slug(),
                project.title(),
                project.descriptions(),
                project.status(),
                project.founder(),
                openRolesByProjectId.getOrDefault(project.id(), List.of()),
                linksByProjectId.getOrDefault(project.id(), List.of()),
                imagesByProjectId.getOrDefault(project.id(), List.of()),
                translationsByProjectId.getOrDefault(project.id(), Map.of()),
                project.createdAt()
            ))
            .toList();
    }

    public static Optional<ProjectDetails> findProjectBySlug(final DataSource dataSource, final String slug) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(PROJECT_DETAILS_SQL)) {
            statement.setString(1, slug);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                final long projectId = resultSet.getLong("id");
                final ProjectAssociations associations = loadProjectAssociations(dataSource, projectId);
                return Optional.of(new ProjectDetails(
                    projectId,
                    resultSet.getLong("owner_user_id"),
                    resultSet.getString("slug"),
                    resultSet.getString("title"),
                    descriptionsFrom(resultSet),
                    resultSet.getString("status"),
                    new FounderInfo(
                        resultSet.getString("founder_public_id"),
                        resultSet.getString("founder_name"),
                        resultSet.getString("founder_role_title"),
                        resultSet.getString("founder_commitment")
                    ),
                    associations.openRoles(),
                    associations.links(),
                    associations.images(),
                    associations.translations(),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load project details", exception);
        }
    }

    public static CreatedProject createProject(final DataSource dataSource, final long ownerUserId, final ProjectInput input) {
        return SqlTransactions.execute(
            dataSource,
            "Unable to create project",
            connection -> {
                final Instant now = Instant.now();
                final String slug = nextSlug(connection, input.title());
                final long projectId = insertProject(connection, ownerUserId, slug, input, now);
                insertFounderRole(connection, projectId, input);
                insertOpenRoles(connection, projectId, input.openRoles());
                ProjectLinksRepository.insertProjectLinks(connection, projectId, input.links());
                return new CreatedProject(projectId, slug);
            }
        );
    }

    public static void updateProject(
        final DataSource dataSource,
        final long projectId,
        final ProjectInput input,
        final boolean clearTranslations
    ) {
        SqlTransactions.execute(
            dataSource,
            "Unable to update project",
            connection -> {
                updateProjectRow(connection, projectId, input);
                if (clearTranslations) {
                    ProjectDescriptionTranslationRepository.deleteTranslations(connection, projectId);
                }
                updateFounderRole(connection, projectId, input);
                replaceOpenRoles(connection, projectId, input.openRoles());
                ProjectLinksRepository.replaceProjectLinks(connection, projectId, input.links());
                return null;
            }
        );
    }

    public static void deleteProject(final DataSource dataSource, final long projectId) {
        final String sql = """
            delete from projects
            where id = ?
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            final int deletedRows = statement.executeUpdate();
            if (deletedRows != 1) {
                throw new IllegalStateException("Expected one project delete for id " + projectId + " but deleted " + deletedRows);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete project", exception);
        }
    }

    private static long insertProject(
        final Connection connection,
        final long ownerUserId,
        final String slug,
        final ProjectInput input,
        final Instant now
    ) throws SQLException {
        final String sql = """
            insert into projects (owner_user_id, slug, title, description_de, description_en, status, created_at, updated_at)
            values (?, ?, ?, ?, ?, 'active', ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, ownerUserId);
            statement.setString(2, slug);
            statement.setString(3, input.title());
            statement.setString(4, input.descriptions().de());
            statement.setString(5, input.descriptions().en());
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
            throw new IllegalStateException("Project insert did not return a generated id");
        }
    }

    private static void updateProjectRow(final Connection connection, final long projectId, final ProjectInput input) throws SQLException {
        final String sql = """
            update projects
            set title = ?, description_de = ?, description_en = ?, updated_at = ?
            where id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, input.title());
            statement.setString(2, input.descriptions().de());
            statement.setString(3, input.descriptions().en());
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.setLong(5, projectId);
            statement.executeUpdate();
        }
    }

    private static void insertFounderRole(final Connection connection, final long projectId, final ProjectInput input) throws SQLException {
        final String sql = """
            insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
            values (?, ?, ?, true, false, 0)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.setString(2, input.founderRole());
            statement.setString(3, input.founderCommitment());
            statement.executeUpdate();
        }
    }

    private static void updateFounderRole(final Connection connection, final long projectId, final ProjectInput input) throws SQLException {
        final String sql = """
            update project_roles
            set title = ?, commitment = ?
            where project_id = ? and is_founder = true
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, input.founderRole());
            statement.setString(2, input.founderCommitment());
            statement.setLong(3, projectId);
            final int updatedRows = statement.executeUpdate();
            if (updatedRows != 1) {
                throw new IllegalStateException("Expected one founder role for project " + projectId + " but updated " + updatedRows);
            }
        }
    }

    private static void replaceOpenRoles(final Connection connection, final long projectId, final List<OpenRole> openRoles) throws SQLException {
        deleteOpenRoles(connection, projectId);
        insertOpenRoles(connection, projectId, openRoles);
    }

    private static void deleteOpenRoles(final Connection connection, final long projectId) throws SQLException {
        final String sql = """
            delete from project_roles
            where project_id = ? and is_founder = false
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.executeUpdate();
        }
    }

    private static void insertOpenRoles(final Connection connection, final long projectId, final List<OpenRole> openRoles) throws SQLException {
        final String sql = """
            insert into project_roles (project_id, title, commitment, is_founder, is_open, sort_order)
            values (?, ?, ?, false, true, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < openRoles.size(); index++) {
                final OpenRole role = openRoles.get(index);
                statement.setLong(1, projectId);
                statement.setString(2, role.title());
                statement.setString(3, role.commitment());
                statement.setInt(4, index + 1);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Map<Long, List<OpenRole>> loadOpenRoles(final DataSource dataSource, final List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }

        final String sql = """
            select
                case when owner.is_deleted = false then role.id end as id,
                role.project_id,
                role.title,
                role.commitment
            from project_roles role
            join projects project on project.id = role.project_id
            join users owner on owner.id = project.owner_user_id
            where role.is_open = true and role.is_founder = false and role.project_id in (%s)
            order by role.project_id, role.sort_order asc, role.id asc
            """.formatted(SqlUtil.placeholders(projectIds.size()));

        final Map<Long, List<OpenRole>> roles = new HashMap<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            SqlUtil.bindLongs(statement, projectIds);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    roles.computeIfAbsent(resultSet.getLong("project_id"), ignored -> new ArrayList<>())
                        .add(new OpenRole(
                            nullableLong(resultSet, "id"),
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

    private static Long nullableLong(final ResultSet resultSet, final String column) throws SQLException {
        final long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static ProjectAssociations loadProjectAssociations(final DataSource dataSource, final long projectId) {
        final List<Long> projectIds = List.of(projectId);
        return new ProjectAssociations(
            loadOpenRoles(dataSource, projectIds).getOrDefault(projectId, List.of()),
            ProjectLinksRepository.loadProjectLinks(dataSource, projectIds).getOrDefault(projectId, List.of()),
            ProjectImagesRepository.loadProjectImages(dataSource, projectIds).getOrDefault(projectId, List.of()),
            ProjectDescriptionTranslationRepository.loadTranslations(dataSource, projectIds).getOrDefault(projectId, Map.of())
        );
    }

    private static ProjectDescriptions descriptionsFrom(final ResultSet resultSet) throws SQLException {
        final String descriptionDe = resultSet.getString("description_de");
        final String descriptionEn = resultSet.getString("description_en");
        return new ProjectDescriptions(
            descriptionDe,
            descriptionEn
        );
    }

    private static String nextSlug(final Connection connection, final String title) throws SQLException {
        final String baseSlug = slugFromTitle(title);
        String slug = baseSlug;
        int suffix = 2;
        while (slugExists(connection, slug)) {
            slug = baseSlug + "-" + suffix;
            suffix++;
        }
        return slug;
    }

    private static boolean slugExists(final Connection connection, final String slug) throws SQLException {
        final String sql = """
            select 1
            from projects
            where slug = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, slug);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static String slugFromTitle(final String title) {
        final String slug = title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "project" : slug;
    }

    public record CreatedProject(long id, String slug) {
    }

    private record ProjectAssociations(
        List<OpenRole> openRoles,
        List<ProjectLink> links,
        List<ProjectImage> images,
        Map<String, ProjectDescriptionTranslation> translations
    ) {
    }
}
