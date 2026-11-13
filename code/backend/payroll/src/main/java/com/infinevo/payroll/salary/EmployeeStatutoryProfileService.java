package com.infinevo.payroll.salary;

import java.util.UUID;

/**
 * Service contract for employee statutory eligibility profiles (W-26.2).
 */
public interface EmployeeStatutoryProfileService {

    StatutoryProfileResponse get(UUID employeeId);

    StatutoryProfileResponse upsert(UUID employeeId, StatutoryProfileRequest request);

    /**
     * D-141: whether the pay run deducts TDS for the employee in the current tenant. An employee with no
     * saved profile counts as applicable. One read of the profile row, no employee lookup, so the pay run
     * can ask per employee within its query budget.
     */
    boolean isTdsApplicable(UUID employeeId);
}
