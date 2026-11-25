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

    public static final BigDecimal EPF_EMPLOYEE_RATE = new BigDecimal("12.0000");
    public static final BigDecimal EPF_EMPLOYER_RATE = new BigDecimal("12.0000");
    public static final BigDecimal EPF_EPS_RATE = new BigDecimal("8.3300");
    public static final BigDecimal EPF_EDLI_RATE = new BigDecimal("0.5000");
    public static final BigDecimal EPF_ADMIN_RATE = new BigDecimal("0.5000");
    public static final BigDecimal EPF_WAGE_CEILING = new BigDecimal("15000.0000");

    public static final BigDecimal ESI_EMPLOYEE_RATE = new BigDecimal("0.7500");
    public static final BigDecimal ESI_EMPLOYER_RATE = new BigDecimal("3.2500");
    public static final BigDecimal ESI_WAGE_CEILING = new BigDecimal("21000.0000");

    public static StatutoryDefaults standard() {
        return new StatutoryDefaults(
                EPF_EMPLOYEE_RATE,
                EPF_EMPLOYER_RATE,
                EPF_EPS_RATE,
                EPF_EDLI_RATE,
                EPF_ADMIN_RATE,
                EPF_WAGE_CEILING,
                ESI_EMPLOYEE_RATE,
                ESI_EMPLOYER_RATE,
                ESI_WAGE_CEILING);
    }
}
