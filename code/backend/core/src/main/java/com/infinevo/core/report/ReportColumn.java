package com.infinevo.core.report;

import java.util.Objects;

/**
 * One column a {@link ReportSource} can export — the allow-list a definition chooses from (W-23.1).
 *
 * @param name the key in each row map and in {@code report_definition.columns}; lowercase snake
 * @param label the header written into the file
 * @param type how the writers render the value
 */
public record ReportColumn(String name, String label, Type type) {

    public ReportColumn {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }

    /** A text column — the common case. */
    public static ReportColumn text(String name, String label) {
        return new ReportColumn(name, label, Type.TEXT);
    }

    /**
     * How a value is written.
     *
     * <p>{@link #NUMBER} is for counts and ids only. A money column is {@link #TEXT}, written from its
     * {@code BigDecimal} as a plain string: a numeric spreadsheet cell is a binary double, which is
     * exactly the imprecision {@code docs/CONVENTIONS.md} section 2 forbids.
     */
    public enum Type {
        TEXT,
        NUMBER,
        DATE,
        TIMESTAMP,
        BOOLEAN
    }
}
