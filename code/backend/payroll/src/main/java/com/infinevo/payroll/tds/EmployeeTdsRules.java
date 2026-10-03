package com.infinevo.payroll.tds;

import com.infinevo.payroll.taxdeclaration.FinancialYear;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * Validation and business rules for TDS records (W-36.1 §4).
 */
public final class EmployeeTdsRules {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^[0-9]{4}-(0[1-9]|1[0-2])$");

    private EmployeeTdsRules() {}

    public static Clock defaultClock() {
        return Clock.system(ZONE);
    }

    public static FinancialYear validateFinancialYear(String fyLabel) {
        if (fyLabel == null || fyLabel.isBlank()) {
            throw new EmployeeTdsValidationException("Financial year is required");
        }
        try {
            return FinancialYear.parse(fyLabel);
        } catch (IllegalArgumentException e) {
            throw new EmployeeTdsValidationException("Invalid financial year: " + e.getMessage());
        }
    }

    public static void validateFigures(TdsFigures figures, FinancialYear fy) {
        if (figures == null) {
            throw new EmployeeTdsValidationException("TDS figures must not be null");
        }

        if (figures.regime() == null) {
            throw new EmployeeTdsValidationException("regime is required and must be OLD or NEW");
        }

        validateMoney("annual_gross", figures.annualGross());
        validateMoney("annual_taxable_income", figures.annualTaxableIncome());
        validateMoney("annual_tax", figures.annualTax());

        if (figures.annualTaxableIncome().compareTo(figures.annualGross()) > 0) {
            throw new EmployeeTdsValidationException("annual_taxable_income (" + figures.annualTaxableIncome()
                    + ") must not exceed annual_gross (" + figures.annualGross() + ")");
        }

        if (figures.effectiveFromPeriod() != null
                && !figures.effectiveFromPeriod().isBlank()) {
            String period = figures.effectiveFromPeriod().trim();
            if (!PERIOD_PATTERN.matcher(period).matches()) {
                throw new EmployeeTdsValidationException(
                        "effective_from_period must match YYYY-MM, was: " + figures.effectiveFromPeriod());
            }
            YearMonth ym = YearMonth.parse(period);
            if (!fy.contains(ym.atDay(1))) {
                throw new EmployeeTdsValidationException(
                        "effective_from_period " + period + " is outside financial year " + fy.label());
            }
        }
    }

    private static void validateMoney(String fieldName, BigDecimal amount) {
        if (amount == null) {
            throw new EmployeeTdsValidationException(fieldName + " is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new EmployeeTdsValidationException(fieldName + " must be non-negative, was: " + amount);
        }
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
            throw new EmployeeTdsValidationException(fieldName + " scale must be at most 2, was: " + amount);
        }
    }
}
