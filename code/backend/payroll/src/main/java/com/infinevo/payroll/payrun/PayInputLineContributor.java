package com.infinevo.payroll.payrun;

import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The {@code PAY_INPUT} contributor (W-29.3 §3): the employee's slice of W-19's ledger for the
 * period, read once per run by the computation, as one line per kind with money in it.
 *
 * <table>
 *   <tr><th>Kind</th><th>Line</th></tr>
 *   <tr><td>OVERTIME</td><td>EARNING, taxable — rows with no amount pay nothing and are counted as unpriced</td></tr>
 *   <tr><td>ONE_TIME_PAYOUT</td><td>EARNING, taxable</td></tr>
 *   <tr><td>REIMBURSEMENT</td><td>REIMBURSEMENT, not taxable</td></tr>
 *   <tr><td>AD_HOC_DEDUCTION</td><td>DEDUCTION</td></tr>
 *   <tr><td>LOP_DAYS</td><td>no money line — a day count {@link PayRunDays} reads</td></tr>
 * </table>
 *
 * <p>A reversal row carries the same positive quantity and amount as the row it reverses and a
 * {@code reverses_id} (W-19), so it is subtracted: a pair in one period nets to nothing. A kind whose
 * net is negative — a reversal posted to a later period than its original — is written on the
 * opposite side under {@code <KIND>_REVERSAL}, so every line amount stays non-negative.
 *
 * <p>This contributor reads the ledger and nothing else: no source module's tables, no HTTP (BUG-005).
 */
@Component
@Order(300)
public class PayInputLineContributor implements PayLineContributor {

    private static final String REVERSAL_SUFFIX = "_REVERSAL";

    @Override
    public List<PayLine> contribute(PayRunEmployeeContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Map<PayInputKind, Money> net = netAmountByKind(ctx.payInputs());
        List<PayLine> lines = new ArrayList<>(4);
        add(lines, PayInputKind.OVERTIME, net, LineKind.EARNING, "Overtime", true);
        add(lines, PayInputKind.ONE_TIME_PAYOUT, net, LineKind.EARNING, "One-time payout", true);
        add(lines, PayInputKind.REIMBURSEMENT, net, LineKind.REIMBURSEMENT, "Reimbursement", false);
        add(lines, PayInputKind.AD_HOC_DEDUCTION, net, LineKind.DEDUCTION, "Ad-hoc deduction", false);
        return lines;
    }

    private static void add(
            List<PayLine> lines,
            PayInputKind kind,
            Map<PayInputKind, Money> net,
            LineKind lineKind,
            String name,
            boolean taxable) {
        Money amount = net.getOrDefault(kind, Money.ZERO);
        if (amount.isZero()) {
            return;
        }
        if (amount.isPositive()) {
            lines.add(new PayLine(lineKind, LineSource.PAY_INPUT, null, kind.name(), name, amount, taxable));
            return;
        }
        LineKind opposite = lineKind == LineKind.DEDUCTION ? LineKind.EARNING : LineKind.DEDUCTION;
        lines.add(new PayLine(
                opposite,
                LineSource.PAY_INPUT,
                null,
                kind.name() + REVERSAL_SUFFIX,
                name + " reversal",
                amount.negate(),
                false));
    }

    /** Σ amount per kind, reversals subtracted; rows with no amount contribute nothing. */
    static Map<PayInputKind, Money> netAmountByKind(List<PayInputResponse> inputs) {
        Map<PayInputKind, Money> net = new EnumMap<>(PayInputKind.class);
        for (PayInputResponse row : nullSafe(inputs)) {
            if (row.amount() == null) {
                continue;
            }
            Money amount = Money.of(row.amount());
            net.merge(row.kind(), row.reversesId() == null ? amount : amount.negate(), Money::add);
        }
        return net;
    }

    /** Σ quantity of {@code LOP_DAYS}, reversals subtracted — the raw request {@link PayRunDays} caps. */
    static BigDecimal netLopDays(List<PayInputResponse> inputs) {
        BigDecimal days = BigDecimal.ZERO;
        for (PayInputResponse row : nullSafe(inputs)) {
            if (row.kind() == PayInputKind.LOP_DAYS && row.quantity() != null) {
                days = row.reversesId() == null ? days.add(row.quantity()) : days.subtract(row.quantity());
            }
        }
        return days;
    }

    /** Overtime rows with hours and no amount, net of reversals of such rows — paid nothing, counted (§13 decision 5). */
    static int unpricedCount(List<PayInputResponse> inputs) {
        int count = 0;
        for (PayInputResponse row : nullSafe(inputs)) {
            if (row.kind() == PayInputKind.OVERTIME && row.amount() == null) {
                count += row.reversesId() == null ? 1 : -1;
            }
        }
        return Math.max(count, 0);
    }

    private static List<PayInputResponse> nullSafe(List<PayInputResponse> inputs) {
        return inputs == null ? List.of() : inputs;
    }
}
