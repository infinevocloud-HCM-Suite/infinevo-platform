package com.infinevo.core.leave;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Exceed balance mode for leave policies (W-16.1, spec section 4 & 6).
 * <ul>
 *   <li>{@code noLimit} - balance may go negative with no limit or consequence.</li>
 *   <li>{@code yearEndLimit} - balance may go negative up to {@code exceed_balance_limit_days}.</li>
 *   <li>{@code markAsLOP} - exceeding balance produces loss of pay in W-16.4a.</li>
 * </ul>
 */
public enum ExceedBalanceMode {
    NO_LIMIT("noLimit"),
    YEAR_END_LIMIT("yearEndLimit"),
    MARK_AS_LOP("markAsLOP");

    private final String dbValue;

    ExceedBalanceMode(String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String getDbValue() {
        return dbValue;
    }

    @JsonCreator
    public static ExceedBalanceMode fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Exceed balance mode must not be null or blank");
        }
        String normalized = raw.trim();
        for (ExceedBalanceMode mode : values()) {
            if (mode.dbValue.equalsIgnoreCase(normalized)
                    || mode.name().replace("_", "").equalsIgnoreCase(normalized.replace("_", ""))) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
                "Unknown exceed balance mode: '" + raw + "'. Supported: noLimit, yearEndLimit, markAsLOP");
    }

    @Converter(autoApply = true)
    public static class JpaConverter implements AttributeConverter<ExceedBalanceMode, String> {
        @Override
        public String convertToDatabaseColumn(ExceedBalanceMode attribute) {
            return attribute != null ? attribute.getDbValue() : null;
        }

        @Override
        public ExceedBalanceMode convertToEntityAttribute(String dbData) {
            return dbData != null ? ExceedBalanceMode.fromString(dbData) : null;
        }
    }
}
