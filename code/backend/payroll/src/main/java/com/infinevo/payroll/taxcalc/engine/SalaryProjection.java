package com.infinevo.payroll.taxcalc.engine;

import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.payroll.taxcalc.model.MonthProjection;
import com.infinevo.payroll.taxcalc.model.SalaryProjectionResult;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure engine for projecting taxable salary across an Indian financial year (W-33.1 spec ? 3, ? 4).
 *
 * <p>For each month from {@code max(fy.start(), dateOfJoining)} to March, evaluates the active
 * salary structure version in force on the first day of that month and sums the monthly amounts
 * of enabled taxable earnings.
 */
public final class SalaryProjection {

    private SalaryProjection() {}

    /**
     * Projects annual taxable salary from dated CTC structure versions.
     *
     * @param fy the financial year
     * @param dateOfJoining employee's date of joining, nullable
     * @param versionInForceResolver function resolving the salary structure in force as of a given date
     * @param isComponentTaxable predicate checking whether an earning component is statutory taxable
     * @return {@link SalaryProjectionResult} with annual taxable sum, monthly breakdowns, and assumptions
     */
    public static SalaryProjectionResult annual(
            FinancialYear fy,
            LocalDate dateOfJoining,
            Function<LocalDate, SalaryVersionResponse> versionInForceResolver,
            Predicate<UUID> isComponentTaxable) {

        Objects.requireNonNull(fy, "fy must not be null");
        Objects.requireNonNull(versionInForceResolver, "versionInForceResolver must not be null");
        Objects.requireNonNull(isComponentTaxable, "isComponentTaxable must not be null");

        List<MonthProjection> monthList = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();
        Money annualSum = Money.ZERO;

        LocalDate startDate = fy.start();
        if (dateOfJoining != null && dateOfJoining.isAfter(fy.start())) {
            startDate = dateOfJoining;
        }

        if (dateOfJoining != null && dateOfJoining.isAfter(fy.end())) {
            assumptions.add(
                    "Employee date of joining (" + dateOfJoining + ") is after financial year end (" + fy.end() + ")");
            return new SalaryProjectionResult(Money.ZERO, List.of(), assumptions);
        }

        YearMonth currentYm = YearMonth.from(startDate);
        YearMonth endYm = YearMonth.from(fy.end());

        while (!currentYm.isAfter(endYm)) {
            LocalDate lookupDate = currentYm.atDay(1);
            if (dateOfJoining != null
                    && YearMonth.from(dateOfJoining).equals(currentYm)
                    && dateOfJoining.isAfter(lookupDate)) {
                // For a mid-month joiner, query version in force as of dateOfJoining so joining month is counted
                lookupDate = dateOfJoining;
            }
            SalaryVersionResponse version = versionInForceResolver.apply(lookupDate);
            if ((version == null || version.cancelled()) && !lookupDate.equals(currentYm.atEndOfMonth())) {
                // Fallback to month end if no version effective on lookup date
                SalaryVersionResponse endOfMonthVersion = versionInForceResolver.apply(currentYm.atEndOfMonth());
                if (endOfMonthVersion != null && !endOfMonthVersion.cancelled()) {
                    version = endOfMonthVersion;
                }
            }

            if (version == null || version.cancelled()) {
                monthList.add(new MonthProjection(currentYm, Money.ZERO, null, "No version in force"));
                assumptions.add("No salary structure in force for " + currentYm);
            } else {
                Money monthlyTaxableSalary = Money.ZERO;
                if (version.earnings() != null) {
                    for (SalaryComponentItemResponse item : version.earnings()) {
                        if (item.enabled() && isComponentTaxable.test(item.componentId())) {
                            BigDecimal amount = item.monthlyAmount();
                            if (amount != null) {
                                monthlyTaxableSalary = monthlyTaxableSalary.add(Money.of(amount));
                            }
                        }
                    }
                }
                monthList.add(new MonthProjection(
                        currentYm,
                        monthlyTaxableSalary,
                        version.id(),
                        "Version effective from " + version.effectiveFrom()));
                annualSum = annualSum.add(monthlyTaxableSalary);
            }

            currentYm = currentYm.plusMonths(1);
        }

        assumptions.add("Salary projected to March with no exit date assumed");
        assumptions.add("Employer NPS (80CCD(2)) not applied (deferred to W-26.x)");

        return new SalaryProjectionResult(annualSum, List.copyOf(monthList), List.copyOf(assumptions));
    }
}
