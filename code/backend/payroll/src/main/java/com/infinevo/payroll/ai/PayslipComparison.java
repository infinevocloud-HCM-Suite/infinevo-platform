package com.infinevo.payroll.ai;

import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.payslip.PayslipLineResponse;
import com.infinevo.payroll.payslip.PayslipResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What changed on one of the caller's own payslips since the month before (W-114.14), and why - only where a stored
 * fact proves it. A pure function of the two payslips and of whether their salary versions differ; no ids in, none
 * out. Every amount is a {@link BigDecimal} and every difference is subtracted as one (rule 4).
 */
final class PayslipComparison {

    /** The closed list of reasons (spec "What the data can prove"). Nothing free-text is ever a reason (rule 6). */
    enum Reason {
        LOSS_OF_PAY,
        LOSS_OF_PAY_REFUNDED,
        MONTH_ONLY_ENTRY,
        NEW_COMPONENT,
        ENDED_COMPONENT,
        SALARY_REVISED,
        PAID_DAYS_CHANGED,
        JOINED_THIS_MONTH,
        FOLLOWS_EARNINGS,
        TAX_SEE_TDS,
        FIRST_PAYSLIP,
        NOT_KNOWN
    }

    /** The four line groups of a payslip. */
    enum Kind {
        EARNING,
        DEDUCTION,
        REIMBURSEMENT,
        BENEFIT
    }

    /** One line: this month's amount, last month's (null when it was not there), the difference and the reasons. */
    record Line(
            Kind kind,
            String code,
            String name,
            BigDecimal amount,
            BigDecimal previousAmount,
            BigDecimal difference,
            List<Reason> reasons) {}

    /** Payable, paid, loss-of-pay and unpaid days of one month. */
    record Days(BigDecimal payable, BigDecimal paid, BigDecimal lossOfPay, BigDecimal unpaid) {}

    /** Gross, deductions and net of one month. */
    record Totals(BigDecimal gross, BigDecimal deductions, BigDecimal net) {}

    record Result(
            String period,
            String comparedWith,
            Days days,
            Days previousDays,
            List<Line> lines,
            List<Line> gone,
            Totals totals,
            Totals previousTotals,
            boolean firstPayslip,
            boolean offCycle) {}

    /** Said with an off-cycle payslip, which is never compared with a regular month. */
    static final String OFF_CYCLE_NOTE = "This is an off-cycle payslip: it pays only the entries made for it, so it"
            + " is not compared with a regular month.";

    private PayslipComparison() {}

    /**
     * @param current this month's paid payslip
     * @param previous the newest earlier regular paid payslip, or {@code null} for the caller's first
     * @param salaryRevised whether the two pay run rows carry different salary versions
     */
    static Result compare(PayslipResponse current, PayslipResponse previous, boolean salaryRevised) {
        Objects.requireNonNull(current, "current must not be null");
        String period = current.run() == null ? null : current.run().period();
        Days days = days(current);
        Totals totals = totals(current);
        if (current.run() != null
                && PayRunType.OFF_CYCLE.name().equals(current.run().type())) {
            // Off-cycle runs carry no structure, loss-of-pay or tax lines (StructureLineContributor,
            // TaxLineContributor):
            // beside a regular month every structure line would look ended. Its lines stand alone.
            List<Line> lines = new ArrayList<>();
            for (Item item : items(current).values()) {
                Reason reason = source(item.line) == LineSource.PAY_INPUT ? Reason.MONTH_ONLY_ENTRY : Reason.NOT_KNOWN;
                lines.add(new Line(
                        item.kind,
                        item.line.code(),
                        item.line.name(),
                        item.line.amount(),
                        null,
                        null,
                        List.of(reason)));
            }
            return new Result(period, null, days, null, lines, List.of(), totals, null, false, true);
        }
        if (previous == null) {
            List<Line> lines = new ArrayList<>();
            for (Map.Entry<String, Item> e : items(current).entrySet()) {
                Item item = e.getValue();
                lines.add(new Line(
                        item.kind,
                        item.line.code(),
                        item.line.name(),
                        item.line.amount(),
                        null,
                        null,
                        List.of(Reason.FIRST_PAYSLIP)));
            }
            return new Result(period, null, days, null, lines, List.of(), totals, null, true, false);
        }

        Days previousDays = days(previous);
        Totals previousTotals = totals(previous);
        boolean paidDaysChanged =
                differs(days.payable(), previousDays.payable()) || differs(days.paid(), previousDays.paid());
        boolean grossChanged = differs(totals.gross(), previousTotals.gross());
        boolean joined = joinedIn(current, period);

        Map<String, Item> now = items(current);
        Map<String, Item> before = items(previous);
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<String, Item> e : now.entrySet()) {
            Item item = e.getValue();
            Item old = before.get(e.getKey());
            BigDecimal amount = orZero(item.line.amount());
            BigDecimal previousAmount = old == null ? null : orZero(old.line.amount());
            BigDecimal difference = previousAmount == null ? amount : amount.subtract(previousAmount);
            List<Reason> reasons = old != null && difference.signum() == 0
                    ? List.of()
                    : reasons(item.line, old == null, salaryRevised, paidDaysChanged, grossChanged, joined);
            lines.add(new Line(
                    item.kind, item.line.code(), item.line.name(), amount, previousAmount, difference, reasons));
        }
        List<Line> gone = new ArrayList<>();
        for (Map.Entry<String, Item> e : before.entrySet()) {
            if (now.containsKey(e.getKey())) {
                continue;
            }
            Item old = e.getValue();
            BigDecimal previousAmount = orZero(old.line.amount());
            gone.add(new Line(
                    old.kind,
                    old.line.code(),
                    old.line.name(),
                    BigDecimal.ZERO,
                    previousAmount,
                    previousAmount.negate(),
                    List.of(goneReason(old.line))));
        }
        String comparedWith = previous.run() == null ? null : previous.run().period();
        return new Result(period, comparedWith, days, previousDays, lines, gone, totals, previousTotals, false, false);
    }

    /** Why a new or changed line is what it is; {@link Reason#NOT_KNOWN} when nothing stored proves a cause. */
    private static List<Reason> reasons(
            PayslipLineResponse line,
            boolean isNew,
            boolean salaryRevised,
            boolean paidDaysChanged,
            boolean grossChanged,
            boolean joined) {
        LineSource source = source(line);
        List<Reason> out = new ArrayList<>();
        if (source == LineSource.LOP) {
            // Days move pay only through this line: structure lines are full-month amounts (StructureLineContributor).
            if (refund(line)) {
                out.add(Reason.LOSS_OF_PAY_REFUNDED);
            } else {
                out.add(Reason.LOSS_OF_PAY);
                if (paidDaysChanged) {
                    out.add(Reason.PAID_DAYS_CHANGED);
                }
                if (joined) {
                    out.add(Reason.JOINED_THIS_MONTH);
                }
            }
        } else if (source == LineSource.PAY_INPUT) {
            out.add(Reason.MONTH_ONLY_ENTRY);
        } else if (source == LineSource.TAX) {
            out.add(Reason.TAX_SEE_TDS);
        } else if (source == LineSource.STATUTORY) {
            if (grossChanged) {
                out.add(Reason.FOLLOWS_EARNINGS);
            }
        } else if (source == LineSource.STRUCTURE) {
            if (isNew) {
                out.add(Reason.NEW_COMPONENT);
            }
            if (salaryRevised) {
                out.add(Reason.SALARY_REVISED);
            }
        }
        if (out.isEmpty()) {
            out.add(Reason.NOT_KNOWN);
        }
        return List.copyOf(out);
    }

    private static Reason goneReason(PayslipLineResponse line) {
        LineSource source = source(line);
        if (source == LineSource.STRUCTURE) {
            return Reason.ENDED_COMPONENT;
        }
        if (source == LineSource.PAY_INPUT) {
            return Reason.MONTH_ONLY_ENTRY;
        }
        if (source == LineSource.LOP) {
            return refund(line) ? Reason.LOSS_OF_PAY_REFUNDED : Reason.LOSS_OF_PAY;
        }
        if (source == LineSource.TAX) {
            return Reason.TAX_SEE_TDS;
        }
        return Reason.NOT_KNOWN;
    }

    /** A loss of pay given back: {@code LOP_REVERSAL} and {@code LOP_BENEFIT_REVERSAL} (LopLineContributor). */
    static boolean refund(PayslipLineResponse line) {
        return line.code() != null && line.code().endsWith("_REVERSAL");
    }

    private static LineSource source(PayslipLineResponse line) {
        if (line.source() == null) {
            return null;
        }
        try {
            return LineSource.valueOf(line.source());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Every line, keyed by group and code; a code given twice in a group keeps the first line. */
    private static Map<String, Item> items(PayslipResponse p) {
        Map<String, Item> out = new LinkedHashMap<>();
        add(out, Kind.EARNING, p.earnings());
        add(out, Kind.DEDUCTION, p.deductions());
        add(out, Kind.REIMBURSEMENT, p.reimbursements());
        add(out, Kind.BENEFIT, p.benefits());
        return out;
    }

    private static void add(Map<String, Item> out, Kind kind, List<PayslipLineResponse> lines) {
        if (lines == null) {
            return;
        }
        for (PayslipLineResponse line : lines) {
            out.putIfAbsent(kind + ":" + line.code(), new Item(kind, line));
        }
    }

    private static Days days(PayslipResponse p) {
        PayslipResponse.DaysSummary d = p.days();
        if (d == null) {
            return new Days(null, null, null, null);
        }
        return new Days(d.payableDays(), d.paidDays(), d.lopDays(), d.unpaidDays());
    }

    private static Totals totals(PayslipResponse p) {
        PayslipResponse.TotalsSummary t = p.totals();
        if (t == null) {
            return new Totals(null, null, null);
        }
        return new Totals(t.grossEarnings(), t.totalDeductions(), t.netPay());
    }

    private static boolean joinedIn(PayslipResponse p, String period) {
        LocalDate joined = p.employee() == null ? null : p.employee().dateOfJoining();
        if (joined == null || period == null) {
            return false;
        }
        try {
            return YearMonth.from(joined).equals(YearMonth.parse(period));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** Both known and different by value (scale ignored). */
    private static boolean differs(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return false;
        }
        return a.compareTo(b) != 0;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Difference of two amounts that may be missing; null when both are. */
    static BigDecimal difference(BigDecimal now, BigDecimal before) {
        if (now == null && before == null) {
            return null;
        }
        return orZero(now).subtract(orZero(before));
    }

    private record Item(Kind kind, PayslipLineResponse line) {}
}
