package com.infinevo.core.employee.detail;

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
        extends EmployeeDetailService<EmployeeIdentificationRequest, EmployeeIdentificationResponse> {}
