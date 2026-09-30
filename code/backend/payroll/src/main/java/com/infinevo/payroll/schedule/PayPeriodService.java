package com.infinevo.payroll.schedule;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Service for deriving pay run period dates (start, end, cutoff, pay date) from a tenant's pay schedule (W-28 §4).
 */
public interface PayPeriodService {

    /**
     * Resolves the period dates for the caller's bound tenant context.
     *
     * @param period the pay period (e.g. 2026-07)
     * @return the derived dates
     * @throws NoPayScheduleException if no pay schedule is configured for the tenant
     * @throws IllegalArgumentException if the period is before the schedule's first period start
     */
    PayPeriodResponse periodFor(YearMonth period);

    /**
     * Resolves the period dates for the given tenant.
     *
     * @param tenantId the tenant ID
     * @param period the pay period
     * @return the derived dates
     * @throws NoPayScheduleException if no pay schedule is configured for the tenant
     * @throws IllegalArgumentException if the period is before the schedule's first period start
     */
    PayPeriodResponse periodFor(UUID tenantId, YearMonth period);
}
