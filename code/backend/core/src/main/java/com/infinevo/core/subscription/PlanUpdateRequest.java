package com.infinevo.core.subscription;

/**
 * Request payload for setting a tenant's plan, support plan and employee limit (W-84.1).
 *
 * <p>Every field is optional and written as given: {@code null} clears it. A {@code null} {@code employeeLimit}
 * means unlimited.
 */
public record PlanUpdateRequest(SubscriptionPlan plan, SupportPlan supportPlan, Integer employeeLimit) {}
