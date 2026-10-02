package com.infinevo.payroll.form16;

/**
 * Deductor (employer) details on Form 16 (W-36.4).
 */
public record DeductorDetails(
        String name,
        String tan,
        String pan,
        String tdsCircle,
        String signatoryName,
        String signatoryParentName,
        String signatoryDesignation) {}
