package com.infinevo.core.subscription;

import java.time.LocalDate;

/**
 * Request payload for setting a tenant's plan, support plan, employee limit and period end (W-84.1, W-86).
 *
 * <p>Every field is optional and written as given: {@code null} clears it. A {@code null} {@code employeeLimit}
 * means unlimited.
 */
public record PlanUpdateRequest(
        SubscriptionPlan plan, SupportPlan supportPlan, Integer employeeLimit, LocalDate currentPeriodEnd) {

    public PlanUpdateRequest(SubscriptionPlan plan, SupportPlan supportPlan, Integer employeeLimit) {
        this(plan, supportPlan, employeeLimit, null);
    }
}
