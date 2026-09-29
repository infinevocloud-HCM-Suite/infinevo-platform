package com.infinevo.payroll.statutory.pt;

import com.infinevo.shared.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for professional tax configurations, overrides, and salary deduction resolution (W-31.2).
 */
public interface ProfessionalTaxService {

    /**
     * Lists professional tax configurations for all distinct states of the tenant's active work locations.
     */
    List<PtStateResponse> statesForTenant();

    /**
     * Retrieves the professional tax configuration for a specific state where the tenant has an active work location.
     */
    PtStateResponse getStateForTenant(String stateCode);

    /**
     * Sets or replaces the tenant-level professional tax override for a state.
     */
    PtStateResponse setOverride(String stateCode, PtOverrideRequest request);

    /**
     * Resets the tenant-level override for a state back to the shared statutory reference slabs.
     */
    void resetOverride(String stateCode);

    /**
     * Returns change history records for a state's override, newest first.
     */
    List<PtHistoryResponse> getHistory(String stateCode);

    /**
     * Resolves the professional tax amount to deduct for an employee in a pay period.
     *
     * @param tenantId the tenant ID
     * @param stateCode the state code of the employee's work location
     * @param gross the employee's gross pay subject to professional tax
     * @param gender the employee's gender
     * @param periodEnd the end date of the pay period
     * @return the resolved professional tax {@link Money}
     */
    Money resolve(UUID tenantId, String stateCode, Money gross, String gender, LocalDate periodEnd);
}
