package com.infinevo.payroll.salary;

import java.util.UUID;

/**
 * Service contract for employee statutory eligibility profiles (W-26.2).
 */
public interface EmployeeStatutoryProfileService {

    StatutoryProfileResponse get(UUID employeeId);

    StatutoryProfileResponse upsert(UUID employeeId, StatutoryProfileRequest request);
}
