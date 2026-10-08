package com.infinevo.core.employee;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published inside the transaction that moves an employee to {@code TERMINATED} (W-73.6 §4), so a
 * module that depends on {@code core} — payroll cancelling the employee's remaining scheduled
 * earnings — can react without {@code core} knowing it exists. Published once per transition, not
 * on a later update that leaves the status unchanged.
 */
public record EmployeeTerminatedEvent(UUID tenantId, UUID employeeId, LocalDate terminationDate) {}
