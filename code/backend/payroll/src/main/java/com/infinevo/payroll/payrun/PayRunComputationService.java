package com.infinevo.payroll.payrun;

import java.util.UUID;

/**
 * Turns a {@code LOCKED}, {@code COMPUTED} or {@code FAILED} run into a {@code COMPUTED} or
 * {@code FAILED} one by running every {@link PayLineContributor} for each included employee
 * (W-29.2 §3). Synchronous; W-29.4 moves the same service onto the worker.
 */
public interface PayRunComputationService {

    /**
     * @throws PayRunNotFoundException if the run is not in the bound tenant
     * @throws IllegalPayRunTransitionException if the run cannot move to {@code COMPUTING}
     */
    PayRunResponse compute(UUID payrunId);
}
