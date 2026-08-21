package io.github.absketches.mitbauen.nativeapp.notifications;

import org.nanonative.nano.services.http.model.HttpObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NotificationsUtil {

    public static final String NOTIFICATIONS_PATH = "/api/notifications";
    public static final String AUTH_REQUIRED_CODE = "NOTIFICATIONS_AUTH_REQUIRED";
    public static final String EMAIL_UNVERIFIED_CODE = "NOTIFICATIONS_EMAIL_UNVERIFIED";
    public static final String METHOD_NOT_ALLOWED_CODE = "METHOD_NOT_ALLOWED";

    private NotificationsUtil() {
    }

    public static boolean matches(final HttpObject request) {
        return NOTIFICATIONS_PATH.equals(request.uri().getPath());
    }

    public static Map<String, Object> notificationsPayload(final List<NotificationItem> notifications) {
        return Map.of("notifications", notifications.stream().map(NotificationsUtil::notificationToMap).toList());
    }

    private static Map<String, Object> notificationToMap(final NotificationItem notification) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", notification.id());
        payload.put("type", notification.type());
        payload.put("projectSlug", notification.projectSlug());
        payload.put("projectTitle", notification.projectTitle());
        payload.put("actorName", notification.actorName());
        payload.put("latestBody", notification.latestBody());
        payload.put("latestAt", notification.latestAt().toString());
        payload.put("unreadCount", notification.unreadCount());
        return payload;
    }
}
