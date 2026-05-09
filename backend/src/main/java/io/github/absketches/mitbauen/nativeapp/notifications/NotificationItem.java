package io.github.absketches.mitbauen.nativeapp.notifications;

import java.time.Instant;

public record NotificationItem(
    String id,
    String type,
    String projectSlug,
    String projectTitle,
    String actorName,
    String latestBody,
    Instant latestAt,
    long unreadCount
) {
}
