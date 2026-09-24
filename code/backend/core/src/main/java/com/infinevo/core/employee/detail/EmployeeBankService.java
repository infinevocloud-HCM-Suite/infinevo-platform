package com.infinevo.core.employee.detail;

/**
 * The {@code bank} section of an employee (W-13.2, spec section 4) —
 * {@code GET} and {@code PUT} on {@code /api/v1/employees/{id}/bank}.
 *
 * <p>The contract and the three failures are on {@link EmployeeDetailService}; this interface only
 * binds it to {@link EmployeeBankRequest} and {@link EmployeeBankResponse}, so a caller holding
 * this service cannot pass another section's body to it. All logic is in
 * {@link EmployeeBankServiceImpl} and {@link AbstractEmployeeDetailServiceImpl}.
 */
public interface EmployeeBankService extends EmployeeDetailService<EmployeeBankRequest, EmployeeBankResponse> {}
