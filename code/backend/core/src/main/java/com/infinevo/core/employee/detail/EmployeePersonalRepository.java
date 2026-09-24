package com.infinevo.core.employee.detail;

/**
 * Reads and writes {@code core.employee_personal} (W-13.2) — {@code V015__employee_personal.sql}.
 *
 * <p>Everything it can do is on {@link EmployeeDetailRepository}, including the warning about the
 * inherited finders that do not name a tenant.
 */
public interface EmployeePersonalRepository extends EmployeeDetailRepository<EmployeePersonal> {}
