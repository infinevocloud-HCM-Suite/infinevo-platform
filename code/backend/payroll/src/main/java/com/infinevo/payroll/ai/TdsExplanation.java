package com.infinevo.payroll.ai;

import com.infinevo.payroll.payslip.PayslipLineResponse;
import com.infinevo.payroll.payslip.PayslipResponse;
import com.infinevo.payroll.taxcalc.recalc.TaxTrigger;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Why the caller's TDS is what it is this month (W-114.14): this month's and last month's TDS line, the year's tax
 * record, and each stored recalculation between the two payslips. A pure function; the month's own working is not
 * stored, so it is never re-derived - only the fixed rule is stated.
 */
final class TdsExplanation {

    static final String RULE =
            "Each month's TDS is the tax still to be deducted for the year, spread over the months left.";
    static final String NO_RECORD = "There is no tax record for this year yet.";

    /** The year's active tax record, as far as the answer needs it. */
    record YearRecord(
            String financialYear,
            String regime,
            String source,
            BigDecimal annualGross,
            BigDecimal annualTaxableIncome,
            BigDecimal annualTax,
            String effectiveFrom,
            BigDecimal deductedSoFar,
            BigDecimal remaining) {}

    /** One stored recalculation: the tenant-zone date it was made, its cause and the annual tax it gave. */
    record Recalculation(LocalDate on, TaxTrigger cause, BigDecimal annualTax) {}

    /** One listed recalculation with the annual tax before it (from the record before it) and after it. */
    record Listed(LocalDate on, TaxTrigger cause, BigDecimal annualTaxBefore, BigDecimal annualTaxAfter) {}

    record Result(
            String period,
            String financialYear,
            BigDecimal tdsThisMonth,
            BigDecimal tdsLastMonth,
            BigDecimal difference,
            YearRecord yearRecord,
            List<Listed> recalculations,
            boolean noRecord) {}

    private TdsExplanation() {}

    /** The financial year (April to March) of a pay month (rule 10). */
    static FinancialYear financialYearOf(YearMonth period) {
        return FinancialYear.containing(period.atDay(1));
    }

    /** The sum of a payslip's TAX lines among its deductions; zero when there is none. */
    static BigDecimal tdsOf(PayslipResponse slip) {
        BigDecimal total = BigDecimal.ZERO;
        if (slip == null || slip.deductions() == null) {
            return total;
        }
        for (PayslipLineResponse line : slip.deductions()) {
            if ("TAX".equals(line.source()) && line.amount() != null) {
                total = total.add(line.amount());
            }
        }
        return total;
    }

    /**
     * @param period this payslip's month
     * @param previousPeriod the previous regular payslip's month, or {@code null}
     * @param tdsThis this month's TDS
     * @param tdsLast last month's TDS, or {@code null} when there is no earlier payslip
     * @param year the active record, or {@code null}
     * @param history every stored recalculation of the financial year, any order
     */
    static Result explain(
            YearMonth period,
            YearMonth previousPeriod,
            BigDecimal tdsThis,
            BigDecimal tdsLast,
            YearRecord year,
            List<Recalculation> history) {
        Objects.requireNonNull(period, "period must not be null");
        FinancialYear fy = financialYearOf(period);
        BigDecimal now = tdsThis == null ? BigDecimal.ZERO : tdsThis;
        BigDecimal before = tdsLast == null ? BigDecimal.ZERO : tdsLast;

        List<Recalculation> sorted = new ArrayList<>(history == null ? List.of() : history);
        sorted.sort(Comparator.comparing(Recalculation::on));
        // After the previous payslip's month end, up to this payslip's month end; with no previous payslip, from the
        // start of the financial year.
        LocalDate from = previousPeriod != null
                ? previousPeriod.atEndOfMonth()
                : LocalDate.of(fy.startYear(), 4, 1).minusDays(1);
        LocalDate to = period.atEndOfMonth();
        List<Listed> listed = new ArrayList<>();
        BigDecimal last = null;
        for (Recalculation r : sorted) {
            if (r.on().isAfter(from) && !r.on().isAfter(to)) {
                listed.add(new Listed(r.on(), r.cause(), last, r.annualTax()));
            }
            last = r.annualTax();
        }
        return new Result(
                period.toString(),
                fy.label(),
                now,
                tdsLast == null ? null : before,
                tdsLast == null ? null : now.subtract(before),
                year,
                List.copyOf(listed),
                year == null);
    }
}
