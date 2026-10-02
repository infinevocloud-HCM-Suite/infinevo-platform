package com.infinevo.payroll.taxcalc.model;

import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.shared.money.Money;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Projected taxable salary breakdown for a single calendar month (W-33.1 spec § 3, § 4).
 *
 * @param month the month projected
 * @param taxableSalary sum of monthly amounts of enabled taxable earnings in force
 * @param salaryVersionId id of the salary structure version in force, nullable
 * @param notes contextual note regarding this month's projection
 * @param version the salary structure version the projection used for this month, nullable. Downstream
 *     month-by-month figures (HRA Basic/HRA, structure EPF, professional tax) must read this rather than
 *     re-resolving a version, so they can never use a different version from the salary they sit beside
 */
public record MonthProjection(
        YearMonth month, Money taxableSalary, UUID salaryVersionId, String notes, SalaryVersionResponse version) {

    public MonthProjection {
        Objects.requireNonNull(month, "month must not be null");
        Objects.requireNonNull(taxableSalary, "taxableSalary must not be null");
    }

    public MonthProjection(YearMonth month, Money taxableSalary, UUID salaryVersionId, String notes) {
        this(month, taxableSalary, salaryVersionId, notes, null);
    }
}
