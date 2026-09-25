package com.infinevo.core.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * One template version as the API returns it (W-20.1), with the placeholders its event supplies so an
 * editor knows what may be used.
 */
public record NotificationTemplateResponse(
        UUID id,
        NotificationEvent event,
        Channel channel,
        String subject,
        String body,
        String locale,
        boolean active,
        LocalDate effectiveFrom,
        Set<String> placeholders,
        Instant updatedAt,
        String updatedBy) {

    public static NotificationTemplateResponse from(NotificationTemplate template) {
        return new NotificationTemplateResponse(
                template.getId(),
                template.getEvent(),
                template.getChannel(),
                template.getSubject(),
                template.getBody(),
                template.getLocale(),
                template.isActive(),
                template.getEffectiveFrom(),
                template.getEvent().placeholders(),
                template.getUpdatedAt(),
                template.getUpdatedBy());
    }
}
