package com.infinevo.core.employee.detail;

/**
 * Reads and writes {@code core.employee_bank} (W-13.2) — {@code V019__employee_bank.sql}.
 *
 * <p>Everything it can do is on {@link EmployeeDetailRepository}, including the warning about the
 * inherited finders that do not name a tenant.
 */
public interface EmployeeBankRepository extends EmployeeDetailRepository<EmployeeBank> {}
