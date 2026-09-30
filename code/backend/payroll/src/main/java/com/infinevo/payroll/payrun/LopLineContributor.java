package com.infinevo.payroll.payrun;

import com.infinevo.core.lop.LopRounding;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The {@code LOP} contributor (W-29.3 §3), after {@link StructureLineContributor}:
 *
 * <pre>
 * LOP line = Σ(pro-rata STRUCTURE earnings) × unpaid_days ÷ basis.divisor, rounded per lop_rounding
 * </pre>
 *
 * <p>One {@code DEDUCTION} line, code {@code LOP} (§13 decision 3). Only components flagged
 * {@code is_pro_rata} are in the base (decision 1): a reimbursement or a bonus that is due is not
 * smaller because someone joined on the 16th — legacy scaled net pay instead ({@code :1172-1174}).
 *
 * <p>Pro-rata benefits are scaled the same way for the employer-cost figure: a {@code BENEFIT} line
 * with source {@code LOP}, code {@code LOP_BENEFIT}, which {@link PayRunTotals} subtracts from
 * {@code total_benefits}. Benefits were never in net pay, so this line does not touch it. It is a
 * separate line because a line amount is never negative (W-29.2 §6).
 *
 * <p>Neither line exceeds its base, so a basis whose divisor is shorter than the calendar month
 * cannot deduct more than the month was worth.
 */
@Component
@Order(200)
public class LopLineContributor implements PayLineContributor {

    static final String LOP_CODE = "LOP";
    static final String LOP_NAME = "Loss of pay";
    static final String LOP_BENEFIT_CODE = "LOP_BENEFIT";
    static final String LOP_BENEFIT_NAME = "Loss of pay - employer benefits";

    @Override
    public List<PayLine> contribute(PayRunEmployeeContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        BigDecimal unpaid = ctx.days().unpaidDays();
        if (unpaid.signum() <= 0) {
            return List.of();
        }
        BigDecimal divisor = ctx.basis().divisor();
        if (divisor == null || divisor.signum() <= 0) {
            throw new IllegalStateException("The working-day basis gave a divisor of " + divisor + " for "
                    + ctx.period() + "; loss of pay cannot be priced");
        }

        Set<UUID> proRata = ctx.proRataComponentIds();
        Money earningBase = Money.ZERO;
        Money benefitBase = Money.ZERO;
        for (PayLine line : ctx.priorLines()) {
            if (line.source() != LineSource.STRUCTURE
                    || line.componentId() == null
                    || !proRata.contains(line.componentId())) {
                continue;
            }
            if (line.kind() == LineKind.EARNING) {
                earningBase = earningBase.add(line.amount());
            } else if (line.kind() == LineKind.BENEFIT) {
                benefitBase = benefitBase.add(line.amount());
            }
        }

        List<PayLine> lines = new ArrayList<>(2);
        Money lop = scaled(earningBase, unpaid, divisor, ctx.lopRounding());
        if (lop.isPositive()) {
            lines.add(new PayLine(LineKind.DEDUCTION, LineSource.LOP, null, LOP_CODE, LOP_NAME, lop, false));
        }
        Money benefitLop = scaled(benefitBase, unpaid, divisor, ctx.lopRounding());
        if (benefitLop.isPositive()) {
            lines.add(new PayLine(
                    LineKind.BENEFIT, LineSource.LOP, null, LOP_BENEFIT_CODE, LOP_BENEFIT_NAME, benefitLop, false));
        }
        return lines;
    }

    /** base × unpaid ÷ divisor — multiplied first so the one rounding carries the least error. */
    static Money scaled(Money base, BigDecimal unpaid, BigDecimal divisor, LopRounding rounding) {
        if (!base.isPositive()) {
            return Money.ZERO;
        }
        Money amount = round(base.multiply(unpaid).divide(divisor), rounding);
        return amount.compareTo(base) > 0 ? base : amount;
    }

    /** W-18.1 §6: the LOP amount is rounded once, by the policy's stamped rule (W-18.2); never a default. */
    static Money round(Money amount, LopRounding rounding) {
        // The stamped rule, never a default one (W-18.2 §9): a figure has a policy or is not written.
        LopRounding mode = Objects.requireNonNull(rounding, "rounding must not be null");
        return switch (mode) {
            case HALF_UP_2 -> Money.of(amount.raw().setScale(2, RoundingMode.HALF_UP));
            case HALF_UP_0 -> Money.of(amount.raw().setScale(0, RoundingMode.HALF_UP));
            case NONE -> amount;
        };
    }
}
