package com.infinevo.core.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Representation of a tenant's reminder rule (W-20.2).
 */
public record ReminderRuleResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("event") NotificationEvent event,
        @JsonProperty("audience") String audience,
        @JsonProperty("anchor") Anchor anchor,
        @JsonProperty("offset_days") int offsetDays,
        @JsonProperty("day_of_week") Integer dayOfWeek,
        @JsonProperty("send_at_local_time") LocalTime sendAtLocalTime,
        @JsonProperty("repeat_every_days") Integer repeatEveryDays,
        @JsonProperty("max_repeats") Integer maxRepeats,
        @JsonProperty("last_executed_at") Instant lastExecutedAt,
        @JsonProperty("repeat_count") int repeatCount,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static ReminderRuleResponse from(ReminderRule r) {
        return new ReminderRuleResponse(
                r.getId(),
                r.getTenantId(),
                r.getEvent(),
                r.getAudience(),
                r.getAnchor(),
                r.getOffsetDays(),
                r.getDayOfWeek(),
                r.getSendAtLocalTime(),
                r.getRepeatEveryDays(),
                r.getMaxRepeats(),
                r.getLastExecutedAt(),
                r.getRepeatCount(),
                r.isActive(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
