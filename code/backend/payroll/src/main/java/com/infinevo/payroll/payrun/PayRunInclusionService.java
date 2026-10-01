package com.infinevo.payroll.payrun;

import com.infinevo.core.employee.EmployeeResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The inclusion rule of W-29.1 §3, replacing the legacy stream filter that dropped people silently. */
public interface PayRunInclusionService {

    /**
     * One decision per employee the run considers, in the order given. An employee outside the
     * period ({@link PayRunInclusionServiceImpl#isConsidered}) gets no decision at all.
     */
    List<InclusionDecision> decide(
            UUID tenantId, List<EmployeeResponse> employees, LocalDate periodStart, LocalDate periodEnd);

    /**
     * An off-cycle run's rows for the employees the officer named, in the order given (W-30.2 §3):
     * bank details required, a salary version recorded when one is in force but never required.
     *
     * @throws EmployeeNotInRunException naming every employee outside the period — the officer named
     *     them and hears why, rather than a silent skip
     */
    List<OffCycleInclusion> forNamed(
            UUID tenantId, List<EmployeeResponse> employees, LocalDate periodStart, LocalDate periodEnd);
}
