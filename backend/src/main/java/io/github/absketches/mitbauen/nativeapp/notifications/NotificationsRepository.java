package io.github.absketches.mitbauen.nativeapp.notifications;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NotificationsRepository {

    private NotificationsRepository() {
    }

    public static List<NotificationItem> listNotifications(final DataSource dataSource, final long userId) {
        final String sql = """
            with watched_projects as (
                select id as project_id
                from projects
                where owner_user_id = ?
                union
                select project_id
                from project_comment_reads
                where user_id = ?
            ),
            unread_comments as (
                select
                    c.project_id,
                    p.slug as project_slug,
                    p.title as project_title,
                    c.body,
                    c.created_at,
                    u.display_name as actor_name,
                    row_number() over (partition by c.project_id order by c.created_at desc, c.id desc) as row_number,
                    count(*) over (partition by c.project_id) as unread_count
                from project_comments c
                join watched_projects wp on wp.project_id = c.project_id
                join projects p on p.id = c.project_id
                join users u on u.id = c.user_id
                left join project_comment_reads r on r.project_id = c.project_id and r.user_id = ?
                where c.user_id <> ?
                    and (r.last_read_at is null or c.created_at > r.last_read_at)
            )
            select project_id, project_slug, project_title, body, created_at, actor_name, unread_count
            from unread_comments
            where row_number = 1
            order by created_at desc, project_id desc
            """;
        final List<NotificationItem> notifications = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, userId);
            statement.setLong(3, userId);
            statement.setLong(4, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    notifications.add(new NotificationItem(
                        "project-comment-" + resultSet.getLong("project_id"),
                        "project_comment",
                        resultSet.getString("project_slug"),
                        resultSet.getString("project_title"),
                        resultSet.getString("actor_name"),
                        resultSet.getString("body"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getLong("unread_count")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load notifications", exception);
        }
        return notifications;
    }
}
