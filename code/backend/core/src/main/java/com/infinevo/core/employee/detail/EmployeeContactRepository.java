package com.infinevo.core.employee.detail;

/**
 * Reads and writes {@code core.employee_contact} (W-13.2) — {@code V016__employee_contact.sql}.
 *
 * <p>Everything it can do is on {@link EmployeeDetailRepository}, including the warning about the
 * inherited finders that do not name a tenant.
 */
public interface EmployeeContactRepository extends EmployeeDetailRepository<EmployeeContact> {}
