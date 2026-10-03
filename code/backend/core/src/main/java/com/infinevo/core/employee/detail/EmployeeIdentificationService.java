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
     * Resolves employee IDs by PAN numbers within the bound tenant for live employees only (W-36.5 §4).
     *
     * <p>A PAN held by two or more live employees is omitted from the resulting map, so the caller can report
     * it as skipped rather than guessing which employee the certificate belongs to.
     *
     * @param pans the set of PAN numbers to resolve
     * @return map from PAN to employee ID
     */
    Map<String, UUID> employeeIdsByPan(Set<String> pans);

    /**
     * Finds PANs that are held by two or more live employees within the bound tenant (W-36.5 §4).
     *
     * @param pans the set of PAN numbers to check
     * @return set of PAN numbers held by multiple live employees
     */
    default Set<String> duplicatePans(Set<String> pans) {
        return Set.of();
    }
}
