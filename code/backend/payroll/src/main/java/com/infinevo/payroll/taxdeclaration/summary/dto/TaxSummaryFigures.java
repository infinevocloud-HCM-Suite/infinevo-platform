package com.infinevo.payroll.taxdeclaration.summary.dto;

import java.math.BigDecimal;

/**
 * The eleven computed tax figures and remaining months produced by W-33 tax computation engine (W-32.4).
 */
public record TaxSummaryFigures(
        BigDecimal taxableIncome,
        BigDecimal netTaxableIncome,
        BigDecimal taxOnTaxableIncome,
        BigDecimal taxYtdAmount,
        BigDecimal taxToBePaid,
        BigDecimal tdsThroughPayroll,
        BigDecimal tdsPreviousEmployer,
        BigDecimal tdsOtherIncome,
        BigDecimal otherSourcesIncome,
        BigDecimal exemptionUnderSection10,
        BigDecimal exemptionUnderSection6a,
        Integer remainingMonths) {}
