package com.infinevo.core.employee;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What a client may say about an employee on create and on update (W-13.1, spec section 4).
 *
 * <p><strong>There is no tenant field, and there must never be one.</strong> The tenant comes from
 * {@code TenantContext}, bound by the filter from the verified token before this record is read. A
 * tenant a caller can state is a tenant a caller can change, which is the single thing the tenancy
 * design exists to prevent.
 *
 * <p>Nor is there an {@code id}, an {@code isDeleted}, or any audit field: identity and lifecycle
 * are the server's, not the caller's. Delete is {@code DELETE /api/v1/employees/{id}} and nothing
 * else.
 *
 * <p>{@code dateOfJoining} and {@code terminationDate} are {@link LocalDate}. Jackson parses
 * {@code "2026-04-01"} into one and rejects {@code "01/04/2026"} — the frozen system takes the
 * string as given
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/BasicDetails.java:44}),
 * so both reach the database and no query can tell them apart.
 *
 * @param portalEnabled null means true — founder decision 2, spec section 13. Absence is the common
 *     case and the common case should not need a flag.
 * @param departmentId the department to assign, or null to clear the assignment — W-14.1. It must
 *     name a record <strong>in the bound tenant</strong>; one belonging to another tenant is refused
 *     as a field error and not as a {@code 404} on the employee, because the employee is fine and the
 *     id is not. See {@code EmployeeServiceImpl.resolve}.
 * @param designationId the designation to assign, same rules
 * @param workLocationId the work location to assign, same rules
 */
public record EmployeeRequest(
        String employeeNumber,
        String firstName,
        String middleName,
        String lastName,
        String gender,
        LocalDate dateOfJoining,
        LocalDate terminationDate,
        EmploymentStatus status,
        String workEmail,
        String mobile,
        Boolean portalEnabled,
        UUID departmentId,
        UUID designationId,
        UUID workLocationId) {}
