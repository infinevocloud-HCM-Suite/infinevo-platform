package com.infinevo.core.notification;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalTime;

/**
 * Request payload for creating or updating a reminder rule (W-20.2).
 */
public record ReminderRuleRequest(
        @JsonProperty("event") NotificationEvent event,
        @JsonProperty("audience") String audience,
        @JsonProperty("anchor") Anchor anchor,
        @JsonProperty("offset_days") @JsonAlias("offsetDays") Integer offsetDays,
        @JsonProperty("day_of_week") @JsonAlias("dayOfWeek") Integer dayOfWeek,
        @JsonProperty("send_at_local_time") @JsonAlias("sendAtLocalTime") LocalTime sendAtLocalTime,
        @JsonProperty("repeat_every_days") @JsonAlias("repeatEveryDays") Integer repeatEveryDays,
        @JsonProperty("max_repeats") @JsonAlias("maxRepeats") Integer maxRepeats) {}
