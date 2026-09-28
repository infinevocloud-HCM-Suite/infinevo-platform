package com.infinevo.core.leave;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Reset frequency options for leave policy (W-16.1, spec section 4 & 6).
 */
public enum ResetFrequency {
    YEARLY("yearly"),
    MONTHLY("monthly"),
    QUARTERLY("quarterly"),
    HALF_YEARLY("halfYearly");

    private final String dbValue;

    ResetFrequency(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @JsonCreator
    public static ResetFrequency fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ResetFrequency freq : values()) {
            if (freq.dbValue.equalsIgnoreCase(normalized)
                    || freq.name().replace("_", "").equalsIgnoreCase(normalized.replace("_", ""))) {
                return freq;
            }
        }
        throw new IllegalArgumentException(
                "Unknown reset frequency: '" + raw + "'. Supported: yearly, monthly, quarterly, halfYearly");
    }

    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<ResetFrequency, String> {
        @Override
        public String convertToDatabaseColumn(ResetFrequency attribute) {
            return attribute != null ? attribute.getDbValue() : null;
        }

        @Override
        public ResetFrequency convertToEntityAttribute(String dbData) {
            return dbData != null ? ResetFrequency.fromString(dbData) : null;
        }
    }
}
