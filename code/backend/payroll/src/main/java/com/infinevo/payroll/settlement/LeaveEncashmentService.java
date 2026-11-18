package com.infinevo.payroll.settlement;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for computing accrued earned/privilege leave encashment and statutory tax exemption (W-90.1).
 */
public interface LeaveEncashmentService {

    /**
     * Estimates leave encashment calculations without persisting.
     */
    LeaveEncashmentResponse estimateEncashment(LeaveEncashmentEstimateRequest request);

    /**
     * Records and persists a leave encashment settlement row for the exiting employee.
     */
    LeaveEncashmentResponse recordSettlement(RecordLeaveEncashmentRequest request);

    /**
     * Retrieves all recorded leave encashment settlements for an employee.
     */
    List<LeaveEncashmentResponse> getSettlements(UUID employeeId);
}
