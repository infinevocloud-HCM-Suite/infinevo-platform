package com.infinevo.core.leave;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Unit of leave measurement (W-16.1, decision 3).
 * Only day-based leave is supported; hour-based leave is rejected.
 */
public enum LeaveUnit {
    DAYS("DAYS");

    private final String value;

    LeaveUnit(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static LeaveUnit fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Leave unit must not be null or blank");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("DAYS".equals(normalized) || "DAY".equals(normalized)) {
            return DAYS;
        }
        throw new IllegalArgumentException(
                "Unsupported leave unit: '" + raw + "'. Only day-based leave (DAYS) is supported.");
    }
}
