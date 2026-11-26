package com.infinevo.core.billing;

import com.infinevo.core.subscription.SubscriptionPlan;
import com.infinevo.core.subscription.SupportPlan;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Subscription pricing matrix calculator (W-94.1 §3).
 *
 * <ul>
 *   <li>{@code STARTER}: ₹49 PEPM, minimum 10 users (₹490/month)
 *   <li>{@code GROWTH}: ₹79 PEPM, minimum 25 users (₹1,975/month)
 *   <li>{@code ENTERPRISE}: ₹119 PEPM, minimum 100 users (₹11,900/month)
 *   <li>{@code PRIORITY} support add-on: ₹4,999/month; {@code STANDARD}: ₹0
 *   <li>{@code ANNUAL}: 15% discount on the plan base amount ({@code billableUsers * PEPM * 12 * 0.85})
 * </ul>
 */
@Component
public class BillingPricingCalculator {

    private static final BigDecimal ANNUAL_DISCOUNT_RATE = new BigDecimal("0.15");
    private static final BigDecimal PRIORITY_SUPPORT_MONTHLY = new BigDecimal("4999.00");

    private record PlanRate(BigDecimal pepmMonthly, int minimumHeadcount) {}

    private static final Map<SubscriptionPlan, PlanRate> PLAN_RATES = Map.of(
            SubscriptionPlan.STARTER, new PlanRate(new BigDecimal("49.00"), 10),
            SubscriptionPlan.GROWTH, new PlanRate(new BigDecimal("79.00"), 25),
            SubscriptionPlan.ENTERPRISE, new PlanRate(new BigDecimal("119.00"), 100));

    public record LineItemSpec(String description, int quantity, BigDecimal unitPrice, BigDecimal amount) {}

    public record PricingResult(
            int billableHeadcount,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal taxableAmount,
            List<LineItemSpec> items) {}

    public PricingResult calculate(
            SubscriptionPlan plan, SupportPlan supportPlan, BillingFrequency frequency, int activeHeadcount) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(frequency, "frequency must not be null");
        if (activeHeadcount < 0) {
            throw new IllegalArgumentException("activeHeadcount must not be negative");
        }

        PlanRate rate = PLAN_RATES.get(plan);
        int billableHeadcount = Math.max(activeHeadcount, rate.minimumHeadcount());
        int months = frequency == BillingFrequency.ANNUAL ? 12 : 1;

        BigDecimal planUnitPrice =
                rate.pepmMonthly().multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal planGross =
                planUnitPrice.multiply(BigDecimal.valueOf(billableHeadcount)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountAmount = frequency == BillingFrequency.ANNUAL
                ? planGross.multiply(ANNUAL_DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        SupportPlan effectiveSupport = supportPlan != null ? supportPlan : SupportPlan.STANDARD;
        BigDecimal supportMonthly = effectiveSupport == SupportPlan.PRIORITY
                ? PRIORITY_SUPPORT_MONTHLY
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal supportAmount =
                supportMonthly.multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal subtotal = planGross.add(supportAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableAmount = subtotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

        List<LineItemSpec> items = new ArrayList<>();
        String headcountNote = activeHeadcount < rate.minimumHeadcount()
                ? billableHeadcount + " billable users (min " + rate.minimumHeadcount() + ")"
                : billableHeadcount + " users";
        items.add(new LineItemSpec(
                plan.name() + " Plan Subscription (" + headcountNote + ", " + frequency.name() + ")",
                billableHeadcount,
                planUnitPrice,
                planGross));

        if (supportAmount.compareTo(BigDecimal.ZERO) > 0) {
            items.add(new LineItemSpec(
                    effectiveSupport.name() + " Support Plan Add-on (" + months + " mo)",
                    months,
                    supportMonthly,
                    supportAmount));
        }

        return new PricingResult(billableHeadcount, subtotal, discountAmount, taxableAmount, List.copyOf(items));
    }
}
