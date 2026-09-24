package com.infinevo.core.employee.detail;

/**
 * Reads and writes {@code core.employee_employment} (W-13.2) — {@code V018__employee_employment.sql}.
 *
 * <p>Everything it can do is on {@link EmployeeDetailRepository}, including the warning about the
 * inherited finders that do not name a tenant.
 */
public interface EmployeeEmploymentRepository extends EmployeeDetailRepository<EmployeeEmployment> {}
