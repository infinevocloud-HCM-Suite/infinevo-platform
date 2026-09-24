package com.infinevo.core.employee.detail;

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
        extends EmployeeDetailService<EmployeePersonalRequest, EmployeePersonalResponse> {}
