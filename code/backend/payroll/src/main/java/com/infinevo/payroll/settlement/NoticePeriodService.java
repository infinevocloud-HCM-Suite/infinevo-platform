package com.infinevo.payroll.settlement;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for calculating and managing notice period payouts, shortfall recoveries, and waivers (W-90.2).
 */
public interface NoticePeriodService {

    /**
     * Calculates notice period settlement without persisting.
     */
    NoticePeriodResponse calculateNoticePeriod(NoticePeriodCalculateRequest request);

    /**
     * Records and persists a notice period settlement row for an exiting employee.
     */
    NoticePeriodResponse recordSettlement(RecordNoticeSettlementRequest request);

    /**
     * Retrieves all recorded notice period settlements for an employee.
     */
    List<NoticePeriodResponse> getSettlements(UUID employeeId);
}
