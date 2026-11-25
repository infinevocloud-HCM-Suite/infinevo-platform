package com.infinevo.payroll.statutory.ecr;

/**
 * Supported file export formats for PF ECR returns (W-87).
 */
public enum PfEcrFormat {
    TXT,
    XLSX,
    CSV;

    public static PfEcrFormat parse(String format) {
        if (format == null || format.isBlank()) {
            return TXT;
        }
        try {
            return PfEcrFormat.valueOf(format.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported ECR format: " + format + ". Supported formats are TXT, XLSX, CSV");
        }
    }
}
