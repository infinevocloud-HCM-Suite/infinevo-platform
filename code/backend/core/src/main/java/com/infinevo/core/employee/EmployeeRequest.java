package com.infinevo.core.employee;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.infinevo.core.employee.detail.EmploymentType;
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
 * @param gender one of {@link Gender} — D-40. {@code "Female"} and {@code "F"} are read as
 *     {@code FEMALE} and stored as the name; anything outside the vocabulary is a field error
 * @param employmentType D-40, <strong>create only</strong>. With the next two it is written to the
 *     Employment section ({@code core.employee_employment}) in the create transaction. On update all
 *     three are ignored: after creation they are the section's, edited through
 *     {@code PUT /api/v1/employees/{id}/employment}, so a client that replaces the root record without
 *     knowing them cannot clear them
 * @param probationEndDate D-40, create only; not before {@code dateOfJoining}
 * @param noticePeriodDays D-40, create only; 0 to 365
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
        UUID workLocationId,
        EmploymentType employmentType,
        LocalDate probationEndDate,
        Integer noticePeriodDays) {

    /** The canonical constructor is the one Jackson binds — named, so the shorter one below cannot be picked. */
    @JsonCreator
    public EmployeeRequest {}

    /**
     * The request as it was before D-40, without the three employment terms. Kept so the callers
     * written against the fourteen-field shape — tests across {@code core}, {@code hrms} and
     * {@code payroll} — still compile unchanged.
     */
    public EmployeeRequest(
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
            UUID workLocationId) {
        this(
                employeeNumber,
                firstName,
                middleName,
                lastName,
                gender,
                dateOfJoining,
                terminationDate,
                status,
                workEmail,
                mobile,
                portalEnabled,
                departmentId,
                designationId,
                workLocationId,
                null,
                null,
                null);
    }
}
