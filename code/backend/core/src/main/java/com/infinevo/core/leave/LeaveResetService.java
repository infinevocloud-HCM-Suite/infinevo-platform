package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for leave reset processing across boundaries with carry-forward rules (W-16.2, spec section 4 &amp; 6).
 */
public interface LeaveResetService {

    /**
     * Executes reset processing for all eligible allocations in the tenant as of the specified date.
     *
     * @param tenantId tenant ID
     * @param asOf evaluation boundary date
     * @return number of allocations reset
     */
    int resetAll(UUID tenantId, LocalDate asOf);

    /**
     * Executes reset processing for a single allocation if eligible and at a boundary.
     *
     * @param tenantId tenant ID
     * @param allocation allocation entity
     * @param asOf evaluation date
     * @param consumedDays days consumed up to this reset boundary
     * @return true if reset was applied, false if skipped
     */
    boolean resetAllocation(UUID tenantId, LeaveAllocation allocation, LocalDate asOf, BigDecimal consumedDays);
}
