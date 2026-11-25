package com.infinevo.core.tenant;

import java.util.UUID;

/**
 * Port for checking whether a tenant has any processed pay runs (W-116).
 * Declared in core and implemented by payroll.
 */
public interface PayRunExistenceChecker {

    /**
     * Returns {@code true} if the given tenant has any pay run that is not CANCELLED.
     */
    boolean hasProcessedPayRun(UUID tenantId);
}
