package com.infinevo.payroll.challan;

import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A quarter of a tax year for the quarterly TDS return (W-89). A pay run belongs to the quarter of the day its
 * TDS was deducted — the day it was paid — because the Form 138 file refuses a payment date outside the quarter.
 * A March salary paid on 2 April is in Q1 of the next year.
 */
public record TdsQuarter(FinancialYear financialYear, String quarter) {

    private static final Set<String> QUARTERS = Set.of("Q1", "Q2", "Q3", "Q4");

    public TdsQuarter {
        if (financialYear == null || quarter == null || !QUARTERS.contains(quarter)) {
            throw new IllegalArgumentException("Quarter must be one of Q1, Q2, Q3, Q4");
        }
    }

    /** Parses "2026-2027" and "q3"; throws {@link IllegalArgumentException} on anything else. */
    public static TdsQuarter parse(String financialYear, String quarter) {
        FinancialYear fy;
        try {
            fy = FinancialYear.parse(financialYear == null ? "" : financialYear.trim());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Financial year must look like 2026-2027, was " + financialYear, e);
        }
        String q = quarter == null ? "" : quarter.trim().toUpperCase(Locale.ROOT);
        if (!QUARTERS.contains(q)) {
            throw new IllegalArgumentException("Quarter must be one of Q1, Q2, Q3, Q4, was " + quarter);
        }
        return new TdsQuarter(fy, q);
    }

    /** The quarter a date falls in: Apr–Jun Q1, Jul–Sep Q2, Oct–Dec Q3, Jan–Mar Q4. */
    public static TdsQuarter of(LocalDate date) {
        FinancialYear fy = FinancialYear.of(date);
        int m = date.getMonthValue();
        String q = m >= 4 && m <= 6 ? "Q1" : m >= 7 && m <= 9 ? "Q2" : m >= 10 ? "Q3" : "Q4";
        return new TdsQuarter(fy, q);
    }

    /** The three salary months of this quarter, in order. */
    public List<YearMonth> months() {
        int first =
                switch (quarter) {
                    case "Q1" -> 0;
                    case "Q2" -> 3;
                    case "Q3" -> 6;
                    default -> 9;
                };
        List<YearMonth> all = financialYear.months();
        return all.subList(first, first + 3);
    }

    /** The quarter a pay run's TDS is reported in. */
    public static TdsQuarter of(PayRun run) {
        return of(deductionDate(run));
    }

    /** Date of deduction and payment: the day the run was paid, else its pay date, else its month's last day. */
    public static LocalDate deductionDate(PayRun run) {
        if (run.getPaidOn() != null) {
            return run.getPaidOn();
        }
        if (run.getPayDate() != null) {
            return run.getPayDate();
        }
        return run.getPeriod().atEndOfMonth();
    }

    /**
     * The salary months whose runs can be paid in this quarter: the month before it starts and its three months.
     * Callers keep only the runs whose {@link #of(PayRun)} is this quarter.
     */
    public List<YearMonth> candidateMonths() {
        List<YearMonth> months = new java.util.ArrayList<>();
        months.add(months().get(0).minusMonths(1));
        months.addAll(months());
        return months;
    }

    public LocalDate start() {
        return months().get(0).atDay(1);
    }

    public LocalDate end() {
        return months().get(2).atEndOfMonth();
    }

    public boolean isQ4() {
        return "Q4".equals(quarter);
    }

    public String label() {
        return financialYear.label() + " " + quarter;
    }
}
