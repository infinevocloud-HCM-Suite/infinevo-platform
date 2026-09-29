package com.infinevo.core.payinput;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * {@code period} is a {@link YearMonth} everywhere in Java and {@code char(7)} {@code YYYY-MM} in
 * SQL (W-19 §4) — Hibernate has no built-in mapping for {@link YearMonth}, so this is the one place
 * the two shapes meet. Local to this package rather than {@code shared}: nothing else has needed a
 * {@code YearMonth} column yet, and {@code AbstractOrgMasterServiceImpl}'s house style is a small
 * helper kept where it is used, not a shared abstraction built ahead of a second caller.
 */
@Converter(autoApply = true)
class YearMonthPeriodConverter implements AttributeConverter<YearMonth, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public String convertToDatabaseColumn(YearMonth attribute) {
        return attribute == null ? null : FORMAT.format(attribute);
    }

    @Override
    public YearMonth convertToEntityAttribute(String dbData) {
        return dbData == null ? null : YearMonth.parse(dbData, FORMAT);
    }
}
