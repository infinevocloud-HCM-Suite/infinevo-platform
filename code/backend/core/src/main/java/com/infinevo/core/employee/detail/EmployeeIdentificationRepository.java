package com.infinevo.core.employee.detail;

/**
 * Reads and writes {@code core.employee_identification} (W-13.2) — {@code V017__employee_identification.sql}.
 *
 * <p>Everything it can do is on {@link EmployeeDetailRepository}, including the warning about the
 * inherited finders that do not name a tenant.
 */
public interface EmployeeIdentificationRepository extends EmployeeDetailRepository<EmployeeIdentification> {}
