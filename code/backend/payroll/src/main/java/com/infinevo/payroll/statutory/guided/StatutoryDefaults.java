package com.infinevo.payroll.statutory.guided;

import java.math.BigDecimal;

/**
 * Standard legal statutory defaults for EPF and ESI (W-93).
 */
public record StatutoryDefaults(
        BigDecimal epfEmployeeRate,
        BigDecimal epfEmployerRate,
        BigDecimal epfEpsRate,
        BigDecimal epfEdliRate,
        BigDecimal epfAdminRate,
        BigDecimal epfWageCeiling,
        BigDecimal esiEmployeeRate,
        BigDecimal esiEmployerRate,
        BigDecimal esiWageCeiling) {

    public static StatutoryDefaults standard() {
        return new StatutoryDefaults(
                new BigDecimal("12.0000"),
                new BigDecimal("12.0000"),
                new BigDecimal("8.3300"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                new BigDecimal("15000.0000"),
                new BigDecimal("0.7500"),
                new BigDecimal("3.2500"),
                new BigDecimal("21000.0000"));
    }
}
