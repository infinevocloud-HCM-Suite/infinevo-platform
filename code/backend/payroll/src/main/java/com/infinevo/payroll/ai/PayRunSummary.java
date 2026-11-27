package com.infinevo.payroll.ai;

import com.infinevo.payroll.payrun.EmployeePayRunResponse;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRunResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A month's regular pay run set against the previous month's (W-114.21): totals and their differences, and six capped
 * lists of what is unusual. A pure function of the two runs and their rows. Rows of the two runs are matched by
 * employee id here, and no id leaves: every list item carries a name, an employee number and figures only.
 *
 * <p>Every amount is a {@link BigDecimal}; the large-change test compares with {@link BigDecimal#compareTo}, never
 * through a floating-point division.
 */
public final class PayRunSummary {

    /** A net pay move this large, as a share of last month's net, is a large change - with {@link #MIN_CHANGE}. */
    static final BigDecimal CHANGE_SHARE = new BigDecimal("0.20");

    /** ... and at least this many rupees, so a small salary does not flood the list. */
    static final BigDecimal MIN_CHANGE = new BigDecimal("1000");

    /** At most this many items in each list; each list carries its full count. */
    static final int LIST_MAX = 20;

    private PayRunSummary() {}

    /** One run's totals. Employer contributions is the sum of the included rows' benefits. */
    public record Totals(
            int included,
            int skipped,
            BigDecimal gross,
            BigDecimal deductions,
            BigDecimal netPay,
            BigDecimal employerContributions) {}

    /** One person in a list: who, and the figures that put them there. */
    public record Item(String name, String employeeNumber, BigDecimal lastNet, BigDecimal thisNet, String note) {}

    /** A capped list: up to {@link #LIST_MAX} items, and how many there were in all. */
    public record Capped(List<Item> items, int total) {}

    public record Result(
            Totals thisRun,
            Totals lastRun,
            int negativeNetCount,
            Capped inThisRunNotLast,
            Capped inLastRunNotThis,
            Capped zeroOrNegativeNet,
            Capped largeChange,
            Capped skipped,
            Capped needsAttention) {}

    /**
     * @param last the previous month's regular run, or {@code null} when there is none
     * @param lastRows its rows; empty when {@code last} is null
     */
    public static Result of(
            PayRunResponse run,
            List<EmployeePayRunResponse> rows,
            PayRunResponse last,
            List<EmployeePayRunResponse> lastRows) {
        Objects.requireNonNull(run, "run must not be null");
        List<EmployeePayRunResponse> these = rows == null ? List.of() : rows;
        List<EmployeePayRunResponse> those = last == null || lastRows == null ? List.of() : lastRows;

        Map<UUID, EmployeePayRunResponse> thisIncluded = included(these);
        Map<UUID, EmployeePayRunResponse> lastIncluded = included(those);

        List<Item> newcomers = new ArrayList<>();
        List<Item> zeroOrNegative = new ArrayList<>();
        List<Item> large = new ArrayList<>();
        List<Item> attention = new ArrayList<>();
        for (EmployeePayRunResponse r : thisIncluded.values()) {
            EmployeePayRunResponse before = lastIncluded.get(r.employeeId());
            BigDecimal net = r.netPay();
            if (last != null && before == null) {
                newcomers.add(item(r, null, net, null));
            }
            if (net != null && net.signum() <= 0) {
                zeroOrNegative.add(item(r, null, net, null));
            }
            if (before != null && isLargeChange(before.netPay(), net)) {
                large.add(item(r, before.netPay(), net, null));
            }
            if (r.unpricedInputCount() > 0) {
                attention.add(item(r, null, null, "unpriced inputs: " + r.unpricedInputCount()));
            } else if (r.computationError() != null && !r.computationError().isBlank()) {
                // The error text may quote data; the model is told only that there is one (rule 6).
                attention.add(item(r, null, null, "computation error"));
            }
        }
        List<Item> leavers = new ArrayList<>();
        for (EmployeePayRunResponse r : lastIncluded.values()) {
            if (!thisIncluded.containsKey(r.employeeId())) {
                leavers.add(item(r, r.netPay(), null, null));
            }
        }
        List<Item> skipped = new ArrayList<>();
        for (EmployeePayRunResponse r : these) {
            if (r.inclusionStatus() == InclusionStatus.SKIPPED) {
                skipped.add(item(
                        r,
                        null,
                        null,
                        r.skipReason() == null ? null : r.skipReason().name()));
            }
        }
        large.sort(Comparator.comparing(
                        (Item i) -> difference(i.lastNet(), i.thisNet()).abs())
                .reversed());

        return new Result(
                totals(run, these),
                last == null ? null : totals(last, those),
                run.negativeNetCount(),
                cap(newcomers),
                cap(leavers),
                cap(zeroOrNegative),
                cap(large),
                cap(skipped),
                cap(attention));
    }

    /**
     * A move of at least {@link #CHANGE_SHARE} of last month's net and at least {@link #MIN_CHANGE}, in either
     * direction: {@code |this - last| >= max(MIN_CHANGE, |last| * CHANGE_SHARE)}.
     */
    static boolean isLargeChange(BigDecimal lastNet, BigDecimal thisNet) {
        if (lastNet == null || thisNet == null) {
            return false;
        }
        BigDecimal move = thisNet.subtract(lastNet).abs();
        if (move.compareTo(MIN_CHANGE) < 0) {
            return false;
        }
        return move.compareTo(lastNet.abs().multiply(CHANGE_SHARE)) >= 0;
    }

    static BigDecimal difference(BigDecimal before, BigDecimal after) {
        return orZero(after).subtract(orZero(before));
    }

    private static Totals totals(PayRunResponse run, List<EmployeePayRunResponse> rows) {
        BigDecimal benefits = BigDecimal.ZERO;
        for (EmployeePayRunResponse r : rows) {
            if (r.inclusionStatus() == InclusionStatus.INCLUDED && r.totalBenefits() != null) {
                benefits = benefits.add(r.totalBenefits());
            }
        }
        return new Totals(
                run.includedCount(),
                run.skippedCount(),
                orZero(run.totalGross()),
                orZero(run.totalDeductions()),
                orZero(run.totalNetPay()),
                benefits);
    }

    private static Map<UUID, EmployeePayRunResponse> included(List<EmployeePayRunResponse> rows) {
        Map<UUID, EmployeePayRunResponse> out = new LinkedHashMap<>();
        for (EmployeePayRunResponse r : rows) {
            if (r.inclusionStatus() == InclusionStatus.INCLUDED && r.employeeId() != null) {
                out.put(r.employeeId(), r);
            }
        }
        return out;
    }

    private static Item item(EmployeePayRunResponse r, BigDecimal lastNet, BigDecimal thisNet, String note) {
        return new Item(r.employeeName(), r.employeeNumber(), lastNet, thisNet, note);
    }

    private static Capped cap(List<Item> items) {
        return new Capped(List.copyOf(items.subList(0, Math.min(items.size(), LIST_MAX))), items.size());
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
