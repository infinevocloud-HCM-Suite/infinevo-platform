package com.infinevo.core.leave;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/**
 * Accrual frequency options for leave policy (W-16.1, spec section 4 & 6).
 */
public enum AccrualFrequency {
    MONTHLY("monthly"),
    YEARLY("yearly");

    private final String dbValue;

    AccrualFrequency(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @JsonCreator
    public static AccrualFrequency fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (AccrualFrequency freq : values()) {
            if (freq.dbValue.equalsIgnoreCase(normalized) || freq.name().equalsIgnoreCase(normalized)) {
                return freq;
            }
        }
        throw new IllegalArgumentException("Unknown accrual frequency: '" + raw + "'. Supported: monthly, yearly");
    }

    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<AccrualFrequency, String> {
        @Override
        public String convertToDatabaseColumn(AccrualFrequency attribute) {
            return attribute != null ? attribute.getDbValue() : null;
        }

        @Override
        public AccrualFrequency convertToEntityAttribute(String dbData) {
            return dbData != null ? AccrualFrequency.fromString(dbData) : null;
        }
    }
}
