package com.infinevo.payroll.taxcalc.model;

import com.infinevo.shared.money.Money;
import java.util.List;
import java.util.Objects;

/**
 * Result of projecting taxable salary for an employee across a financial year (W-33.1 spec ? 3, ? 4).
 *
 * @param annualTaxableSalary projected taxable gross salary for the year
 * @param months per-month projections
 * @param assumptions audit list of assumptions made during projection
 */
public record SalaryProjectionResult(
        Money annualTaxableSalary, List<MonthProjection> months, List<String> assumptions) {

    public SalaryProjectionResult {
        Objects.requireNonNull(annualTaxableSalary, "annualTaxableSalary must not be null");
        months = months == null ? List.of() : List.copyOf(months);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
    }
}
