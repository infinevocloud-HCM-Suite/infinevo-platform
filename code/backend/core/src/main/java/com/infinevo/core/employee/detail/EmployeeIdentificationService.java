package com.infinevo.core.employee.detail;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The {@code identification} section of an employee (W-13.2, spec section 4) —
 * {@code GET} and {@code PUT} on {@code /api/v1/employees/{id}/identification}.
 *
 * <p>The contract and the three failures are on {@link EmployeeDetailService}; this interface only
 * binds it to {@link EmployeeIdentificationRequest} and {@link EmployeeIdentificationResponse}, so a caller holding
 * this service cannot pass another section's body to it. All logic is in
 * {@link EmployeeIdentificationServiceImpl} and {@link AbstractEmployeeDetailServiceImpl}.
 */
public interface EmployeeIdentificationService
        extends EmployeeDetailService<EmployeeIdentificationRequest, EmployeeIdentificationResponse> {

    /**
     * The live employee holding each PAN in the bound tenant (W-36.5, spec section 4).
     *
     * <p>PANs are trimmed and upper-cased before matching — stored PANs are upper case, because
     * {@link EmployeeIdentificationServiceImpl} refuses any other — and the keys of the result are
     * the upper-cased PANs. A PAN no live employee holds is absent. <strong>A PAN held by two or more
     * live employees is absent too</strong>: filing a certificate against one of them would be a
     * guess, and the caller reports it as skipped.
     *
     * <p>One query, whatever the size of {@code pans}. The tenant comes from {@code TenantContext}.
     *
     * @param pans the PANs to look up; {@code null} and blank entries are ignored
     * @return upper-cased PAN to employee id, never {@code null}
     */
    Map<String, UUID> employeeIdsByPan(Set<String> pans);
}
