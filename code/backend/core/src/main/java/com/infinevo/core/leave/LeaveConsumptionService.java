package com.infinevo.core.leave;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Service managing leave consumption logging, loss-of-pay derivation, and pay-input ledger posting (W-16.4a).
 */
public interface LeaveConsumptionService {

    /**
     * Consumes balance for an approved leave request.
     * Idempotent: duplicate invocations for the same leave request produce no additional rows.
     */
    void consume(LeaveRequest request);

    /**
     * Reverses consumption and loss-of-pay records for a cancelled leave request.
     */
    void cancel(LeaveRequest request, String reason);

    /**
     * Returns consumption rows for an employee in order, optionally filtered by calendar year.
     */
    List<LeaveConsumptionResponse> getConsumption(UUID employeeId, Integer year);

    /**
     * Returns monthly LOP details with delta row breakdown for an employee and period.
     */
    LopResponse getLop(UUID employeeId, YearMonth period);
}
