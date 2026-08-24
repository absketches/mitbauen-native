package io.github.absketches.mitbauen.nativeapp.jobs.repository;

import io.github.absketches.mitbauen.nativeapp.jobs.model.JobApplicationCreateResult;
import io.github.absketches.mitbauen.nativeapp.jobs.model.JobApplicationTarget;
import io.github.absketches.mitbauen.nativeapp.jobs.model.JobListing;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JobsRepository {

    private static final int JOBS_LIMIT = 200;

    private static final String JOBS_SQL = """
        select
            open_role.id as role_id,
            p.slug as project_slug,
            p.title as project_title,
            open_role.title as role_title,
            open_role.commitment as role_commitment
        from project_roles open_role
        join projects p on p.id = open_role.project_id
        join users u on u.id = p.owner_user_id
        where open_role.is_open = true
            and open_role.is_founder = false
            and p.status = 'active'
            and u.is_deleted = false
        order by p.created_at desc, p.id desc, open_role.sort_order asc, open_role.id asc
        limit ?
        """;

    private JobsRepository() {
    }

    public static List<JobListing> listJobs(final DataSource dataSource) {
        final List<JobListing> jobs = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(JOBS_SQL)) {
            statement.setInt(1, JOBS_LIMIT);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    jobs.add(new JobListing(
                        resultSet.getLong("role_id"),
                        resultSet.getString("project_slug"),
                        resultSet.getString("project_title"),
                        resultSet.getString("role_title"),
                        resultSet.getString("role_commitment")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load jobs", exception);
        }

        return jobs;
    }

    public static Optional<JobApplicationTarget> findApplicationTarget(final DataSource dataSource, final long roleId) {
        final String sql = """
            select
                open_role.id as role_id,
                open_role.title as role_title,
                p.title as project_title,
                u.display_name as owner_name,
                u.email as owner_email
            from project_roles open_role
            join projects p on p.id = open_role.project_id
            join users u on u.id = p.owner_user_id
            where open_role.id = ?
                and open_role.is_open = true
                and open_role.is_founder = false
                and p.status = 'active'
                and u.is_deleted = false
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, roleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new JobApplicationTarget(
                    resultSet.getLong("role_id"),
                    resultSet.getString("role_title"),
                    resultSet.getString("project_title"),
                    resultSet.getString("owner_name"),
                    resultSet.getString("owner_email")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load job application target", exception);
        }
    }

    public static JobApplicationCreateResult createApplication(
        final DataSource dataSource,
        final long roleId,
        final long applicantUserId,
        final String fit,
        final String availability
    ) {
        final String sql = """
            insert into job_applications (role_id, applicant_user_id, fit, availability, created_at)
            select open_role.id, ?, ?, ?, current_timestamp
            from project_roles open_role
            join projects p on p.id = open_role.project_id
            join users u on u.id = p.owner_user_id
            where open_role.id = ?
                and open_role.is_open = true
                and open_role.is_founder = false
                and p.status = 'active'
                and u.is_deleted = false
            on conflict (role_id, applicant_user_id) do nothing
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, applicantUserId);
            statement.setString(2, fit);
            statement.setString(3, availability);
            statement.setLong(4, roleId);
            return statement.executeUpdate() == 1
                ? JobApplicationCreateResult.CREATED
                : JobApplicationCreateResult.DUPLICATE_OR_UNAVAILABLE;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create job application", exception);
        }
    }

    public static void deleteApplication(final DataSource dataSource, final long roleId, final long applicantUserId) {
        final String sql = """
            delete from job_applications
            where role_id = ? and applicant_user_id = ?
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, roleId);
            statement.setLong(2, applicantUserId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete job application", exception);
        }
    }
}
