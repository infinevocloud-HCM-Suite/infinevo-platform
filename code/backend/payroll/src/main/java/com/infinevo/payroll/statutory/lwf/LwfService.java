package com.infinevo.payroll.statutory.lwf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service managing Labour Welfare Fund (LWF) statutory rules, tenant settings, and computation (W-92).
 */
public interface LwfService {

    /**
     * Lists LWF settings for all states where the tenant has active work locations.
     */
    List<LwfStateSettingResponse> listSettings(UUID tenantId);

    /**
     * Retrieves the LWF setting for a specific state.
     */
    LwfStateSettingResponse getStateSetting(UUID tenantId, String stateCode);

    /**
     * Updates tenant LWF registration number and/or enabled status for a state.
     */
    LwfStateSettingResponse updateSetting(
            UUID tenantId, String stateCode, LwfSettingUpdateRequest request, String actor);

    /**
     * Computes the employee deduction and employer contribution for an employee in a pay period.
     */
    LwfCalculationResult calculate(
            UUID tenantId, String stateCode, BigDecimal grossEarnings, int month, LocalDate periodEnd);

    /**
     * Exports the LWF periodic return statement for a regular pay run.
     */
    byte[] exportReturnFile(UUID tenantId, UUID payrunId, String format);
}
