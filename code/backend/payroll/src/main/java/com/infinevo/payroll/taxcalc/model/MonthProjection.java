package com.infinevo.payroll.taxcalc.model;

import com.infinevo.shared.money.Money;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Projected taxable salary breakdown for a single calendar month (W-33.1 spec ? 3, ? 4).
 *
 * @param month the month projected
 * @param taxableSalary sum of monthly amounts of enabled taxable earnings in force
 * @param salaryVersionId id of the salary structure version in force, nullable
 * @param notes contextual note regarding this month's projection
 */
public record MonthProjection(YearMonth month, Money taxableSalary, UUID salaryVersionId, String notes) {

    public MonthProjection {
        Objects.requireNonNull(month, "month must not be null");
        Objects.requireNonNull(taxableSalary, "taxableSalary must not be null");
    }
}
