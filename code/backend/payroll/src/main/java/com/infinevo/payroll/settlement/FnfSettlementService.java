package com.infinevo.payroll.settlement;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Full & Final Settlement calculation and lifecycle management (W-90.3).
 */
public interface FnfSettlementService {

    /**
     * Consolidates exit month pro-rated salary, leave encashment dues, notice buyout/shortfall,
     * gratuity, recoveries, and statutory deductions to produce an F&F settlement.
     */
    FnfSettlementResponse initiateSettlement(FnfInitiateRequest request);

    /**
     * Retrieves an F&F settlement by its ID.
     */
    FnfSettlementResponse getSettlement(UUID id);

    /**
     * Retrieves all recorded F&F settlements for a given employee.
     */
    List<FnfSettlementResponse> getSettlementsByEmployee(UUID employeeId);

    /**
     * Lists all recorded F&F settlements in the current tenant.
     */
    List<FnfSettlementResponse> listSettlements();

    /**
     * Approves an F&F settlement in DRAFT or UNDER_REVIEW status.
     */
    FnfSettlementResponse approveSettlement(UUID id, String approver);

    /**
     * Finalizes and marks an approved F&F settlement as PAID, triggering exit status transition.
     */
    FnfSettlementResponse paySettlement(UUID id);
}
