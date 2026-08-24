package io.github.absketches.mitbauen.nativeapp.projects.translation;

import io.github.absketches.mitbauen.nativeapp.db.SqlUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProjectDescriptionTranslationRepository {

    private ProjectDescriptionTranslationRepository() {
    }

    public static Map<Long, Map<String, ProjectDescriptionTranslation>> loadTranslations(final DataSource dataSource, final List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }

        final String sql = """
            select project_id, source_language, target_language, source_text_hash, translated_text, provider, model
            from project_description_translations
            where project_id in (%s)
            """.formatted(SqlUtil.placeholders(projectIds.size()));

        final Map<Long, Map<String, ProjectDescriptionTranslation>> translations = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            SqlUtil.bindLongs(statement, projectIds);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final long projectId = resultSet.getLong("project_id");
                    final ProjectDescriptionTranslation translation = translationFrom(resultSet);
                    translations.computeIfAbsent(projectId, ignored -> new LinkedHashMap<>())
                        .put(translation.targetLanguage(), translation);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load project description translations", exception);
        }
        return translations;
    }

    public static Optional<ProjectDescriptionTranslation> findTranslation(
        final DataSource dataSource,
        final long projectId,
        final String sourceLanguage,
        final String targetLanguage
    ) {
        final String sql = """
            select source_language, target_language, source_text_hash, translated_text, provider, model
            from project_description_translations
            where project_id = ? and source_language = ? and target_language = ?
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.setString(2, sourceLanguage);
            statement.setString(3, targetLanguage);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(translationFrom(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to find project description translation", exception);
        }
    }

    public static void upsertTranslation(final DataSource dataSource, final long projectId, final ProjectDescriptionTranslation translation) {
        final String sql = """
            insert into project_description_translations (
                project_id, source_language, target_language, source_text_hash, translated_text, provider, model, created_at
            )
            values (?, ?, ?, ?, ?, ?, ?, current_timestamp)
            on conflict (project_id, source_language, target_language)
            do update set
                source_text_hash = excluded.source_text_hash,
                translated_text = excluded.translated_text,
                provider = excluded.provider,
                model = excluded.model,
                created_at = current_timestamp
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.setString(2, translation.sourceLanguage());
            statement.setString(3, translation.targetLanguage());
            statement.setString(4, translation.sourceTextHash());
            statement.setString(5, translation.translatedText());
            statement.setString(6, translation.provider());
            statement.setString(7, translation.model());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save project description translation", exception);
        }
    }

    public static void deleteTranslations(final Connection connection, final long projectId) throws SQLException {
        final String sql = """
            delete from project_description_translations
            where project_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, projectId);
            statement.executeUpdate();
        }
    }

    private static ProjectDescriptionTranslation translationFrom(final ResultSet resultSet) throws SQLException {
        return new ProjectDescriptionTranslation(
            resultSet.getString("source_language"),
            resultSet.getString("target_language"),
            resultSet.getString("source_text_hash"),
            resultSet.getString("translated_text"),
            resultSet.getString("provider"),
            resultSet.getString("model")
        );
    }
}
