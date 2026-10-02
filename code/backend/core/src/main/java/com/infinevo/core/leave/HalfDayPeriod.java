package com.infinevo.core.leave;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Half-day period enumeration (W-16.3, spec section 4 &amp; 6).
 * Stored in database as lowercase {@code 'first'} or {@code 'second'}.
 */
public enum HalfDayPeriod {
    FIRST("first"),
    SECOND("second");

    private final String dbValue;

    HalfDayPeriod(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @JsonCreator
    public static HalfDayPeriod fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        for (HalfDayPeriod period : values()) {
            if (period.dbValue.equalsIgnoreCase(normalized) || period.name().equalsIgnoreCase(normalized)) {
                return period;
            }
        }
        throw new IllegalArgumentException("Unknown half-day period: '" + raw + "'. Supported: first, second");
    }

    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<HalfDayPeriod, String> {
        @Override
        public String convertToDatabaseColumn(HalfDayPeriod attribute) {
            return attribute != null ? attribute.getDbValue() : null;
        }

        @Override
        public HalfDayPeriod convertToEntityAttribute(String dbData) {
            return dbData != null ? HalfDayPeriod.fromString(dbData) : null;
        }
    }
}
