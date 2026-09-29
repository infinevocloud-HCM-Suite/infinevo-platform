package com.infinevo.core.notification;

import java.time.Instant;
import java.util.UUID;

/** One in-app notification as its recipient sees it (W-20.1). */
public record NotificationResponse(
        UUID id,
        NotificationEvent event,
        String subject,
        String body,
        String subjectRef,
        Instant queuedAt,
        Instant readAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEvent(),
                notification.getSubject(),
                notification.getBody(),
                notification.getSubjectRef(),
                notification.getQueuedAt(),
                notification.getReadAt());
    }
}
