package com.infinevo.core.report;

/**
 * The two file formats an export can take (W-23.1). PDF is out of scope (spec section 2): neither
 * frozen product produces one, and payslip rendering is {@code W-36}'s.
 *
 * <p>Stored as the name in {@code core.report_definition.format}, which a {@code CHECK} constrains to
 * the same two values ({@code V040__report_definition.sql}).
 */
public enum ExportFormat {
    CSV("csv"),
    XLSX("xlsx");

    private final String extension;

    ExportFormat(String extension) {
        this.extension = extension;
    }

    /** The file extension the document store reads the type from ({@code DocumentType}). */
    public String extension() {
        return extension;
    }
}
