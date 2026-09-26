package com.infinevo.core.employee;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One employee as the API returns it (W-13.1, spec section 4).
 *
 * <p>{@code tenantId} is included for the same reason {@code MeController.MeView} includes it: the
 * client never chose it, so seeing which tenant answered is useful rather than redundant. It is
 * output only — {@link EmployeeRequest} has no such field.
 *
 * <p>{@code isDeleted} is absent: a soft-deleted employee is never returned at all, so a flag saying
 * so would only ever read {@code false} and would invite a client to ask for the deleted ones.
 *
 * <p>The three org assignments are returned as ids and not as nested objects — W-14.1. The lists
 * behind them are {@code GET /api/v1/departments} and its two siblings, which a client fetches once
 * and reuses; inlining a name here would make every employee read a four-table join and would put two
 * copies of the department's name in the client's hands to drift apart.
 */
public record EmployeeResponse(
        UUID id,
        UUID tenantId,
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
        boolean portalEnabled,
        UUID userAccountId,
        UUID departmentId,
        UUID designationId,
        UUID workLocationId,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Repacks a persisted employee. The only way one of these is built.
     *
     * <p>The three associations are lazy and only their ids are read — but reading the id
     * <strong>does</strong> initialise the proxy here, so this is up to three extra SELECTs, not
     * one row read. Hibernate can answer {@code getId()} from a proxy only when the identifier is
     * annotated on a getter; {@link com.infinevo.core.org.OrgMaster} annotates the field, so there
     * is no identifier getter to intercept and the proxy loads.
     *
     * <p>That costs nothing today: there is no employee list endpoint, and all three callers run
     * inside a transaction. <strong>It will matter to W-13.3</strong>, whose search and listing
     * turns this into N+1 across a page of employees. The fix then is a projection or an explicit
     * {@code @EntityGraph} — not a comment claiming the problem is not there, which is what this
     * said until the W-14.1 review read it.
     */
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getTenantId(),
                employee.getEmployeeNumber(),
                employee.getFirstName(),
                employee.getMiddleName(),
                employee.getLastName(),
                employee.getGender(),
                employee.getDateOfJoining(),
                employee.getTerminationDate(),
                employee.getStatus(),
                employee.getWorkEmail(),
                employee.getMobile(),
                employee.isPortalEnabled(),
                employee.getUserAccountId(),
                employee.getDepartment() == null
                        ? null
                        : employee.getDepartment().getId(),
                employee.getDesignation() == null
                        ? null
                        : employee.getDesignation().getId(),
                employee.getWorkLocation() == null
                        ? null
                        : employee.getWorkLocation().getId(),
                employee.getCreatedAt(),
                employee.getUpdatedAt());
    }
}
