package com.infinevo.core.subscription;

import com.infinevo.shared.entitlement.PlatformModule;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Representation of a tenant's subscription and active modules (W-12.1), with its plan, support plan and employee
 * limit (W-84.1; a {@code null} limit is unlimited).
 */
public record SubscriptionResponse(
        UUID id,
        UUID tenantId,
        SubscriptionStatus status,
        Set<PlatformModule> modules,
        LocalDate startedOn,
        LocalDate currentPeriodEnd,
        String externalRef,
        SubscriptionPlan plan,
        SupportPlan supportPlan,
        Integer employeeLimit) {}
