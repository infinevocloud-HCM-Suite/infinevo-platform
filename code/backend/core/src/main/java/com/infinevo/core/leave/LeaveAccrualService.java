package com.infinevo.core.leave;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for scheduled and on-demand leave accrual processing (W-16.2, spec section 4 &amp; 6).
 */
public interface LeaveAccrualService {

    /**
     * Accrues leave for all eligible allocations in the tenant as of the specified date.
     *
     * @param tenantId tenant ID
     * @param asOf evaluation date
     * @return number of allocations accrued
     */
    int accrueAll(UUID tenantId, LocalDate asOf);

    /**
     * Accrues leave for a single allocation if eligible and not already accrued for this period.
     *
     * @param tenantId tenant ID
     * @param allocation allocation entity
     * @param asOf evaluation date
     * @return true if accrued, false if skipped (not enabled or already accrued)
     */
    boolean accrueAllocation(UUID tenantId, LeaveAllocation allocation, LocalDate asOf);
}
