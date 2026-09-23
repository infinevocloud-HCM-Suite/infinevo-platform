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
        Instant createdAt,
        Instant updatedAt) {

    /** Repacks a persisted employee. The only way one of these is built. */
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
                employee.getCreatedAt(),
                employee.getUpdatedAt());
    }
}
