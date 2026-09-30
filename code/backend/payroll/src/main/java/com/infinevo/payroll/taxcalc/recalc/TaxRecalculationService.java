package com.infinevo.payroll.taxcalc.recalc;

import java.util.List;
import java.util.UUID;

/**
 * Service for triggering tax recalculations, persisting immutable audit history,
 * and querying historical calculations (W-33.3).
 */
public interface TaxRecalculationService {

    /**
     * Recalculates tax for the given employee, financial year, and trigger.
     * System-triggered (computedBy is null).
     */
    TaxComputationRecord recalculate(UUID employeeId, String financialYear, TaxTrigger trigger);

    /**
     * Recalculates tax for the given employee, financial year, trigger, and officer.
     */
    TaxComputationRecord recalculate(UUID employeeId, String financialYear, TaxTrigger trigger, UUID computedBy);

    /**
     * Queries computation history for an employee in a financial year, newest first.
     */
    List<TaxComputationRecordResponse> history(UUID employeeId, String financialYear);

    /**
     * Queries computation history for the currently logged-in self-service employee.
     */
    List<TaxComputationRecordResponse> historyOwn(String financialYear);
}
