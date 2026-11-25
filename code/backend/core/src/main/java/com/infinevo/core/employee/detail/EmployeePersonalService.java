package com.infinevo.core.employee.detail;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code personal} section of an employee (W-13.2, spec section 4) —
 * {@code GET} and {@code PUT} on {@code /api/v1/employees/{id}/personal}.
 *
 * <p>The contract and the three failures are on {@link EmployeeDetailService}; this interface only
 * binds it to {@link EmployeePersonalRequest} and {@link EmployeePersonalResponse}, so a caller holding
 * this service cannot pass another section's body to it. All logic is in
 * {@link EmployeePersonalServiceImpl} and {@link AbstractEmployeeDetailServiceImpl}.
 */
public interface EmployeePersonalService
        extends EmployeeDetailService<EmployeePersonalRequest, EmployeePersonalResponse> {

    /**
     * D-154: the employee's date of birth in the bound tenant, for system-side computation - the tax
     * calculator's age category, the EPS age at salary save and in the pay run.
     *
     * <p>Not guarded by {@code core.employee.read}, deliberately, unlike {@link #get(UUID)}. Its callers
     * compute on the employee's behalf, often with no user at all (the asynchronous tax recalculation),
     * and must not depend on who triggered them. It returns one date and nothing else, and no endpoint
     * exposes it; the user-facing read stays {@link #get(UUID)}, which keeps its check.
     *
     * <p>Still tenant-scoped: the tenant is read from {@code TenantContext} (an unbound one throws) and
     * row-level security applies. Empty when the employee is not live in the bound tenant, has no
     * personal section, or has no date of birth recorded.
     */
    Optional<LocalDate> dateOfBirth(UUID employeeId);
}
