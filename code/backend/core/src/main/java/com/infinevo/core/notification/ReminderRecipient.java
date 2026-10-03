package com.infinevo.core.notification;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One person a reminder goes to, with the values only their audience can supply (W-43.1).
 *
 * <p>The sweep fills every value it can for each recipient (name, week, due date); an audience adds its own through
 * {@code placeholders}, and where a name clashes the audience's value wins. Never null: an audience with nothing to
 * add gives an empty map.
 *
 * @param employeeId the employee the reminder is composed for
 * @param placeholders the audience's own values for this recipient, by placeholder name
 */
public record ReminderRecipient(UUID employeeId, Map<String, Object> placeholders) {

    public ReminderRecipient {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
    }

    /** A recipient the audience adds no values for. */
    public static ReminderRecipient of(UUID employeeId) {
        return new ReminderRecipient(employeeId, Map.of());
    }
}
