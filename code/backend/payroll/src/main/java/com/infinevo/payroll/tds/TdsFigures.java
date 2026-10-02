package com.infinevo.payroll.tds;

import com.infinevo.payroll.taxcalc.TaxRegime;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Value object conveying annual TDS computation figures to be recorded (W-36.1 §4).
 */
public record TdsFigures(
        TaxRegime regime,
        BigDecimal annualGross,
        BigDecimal annualTaxableIncome,
        BigDecimal annualTax,
        String effectiveFromPeriod,
        UUID declarationId,
        String note) {}
