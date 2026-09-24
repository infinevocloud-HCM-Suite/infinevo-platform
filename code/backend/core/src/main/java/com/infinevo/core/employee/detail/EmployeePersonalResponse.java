package com.infinevo.core.employee.detail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An employee's personal section as the API returns it (W-13.2, spec section 4).
 *
 * <p>{@code employeeId} is included because the section is meaningless without the person it belongs
 * to; {@code tenantId} for the same reason {@code EmployeeResponse} includes it — the client never
 * chose it, so seeing which tenant answered is useful rather than redundant. Both are output only.
 */
public record EmployeePersonalResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        LocalDate dateOfBirth,
        String maritalStatus,
        String nationality,
        String ethnicity,
        String fatherName,
        String differentlyAbledType,
        boolean eligibleForFullTaxExemption,
        Instant createdAt,
        Instant updatedAt) {

    /** Repacks a persisted section. The only way one of these is built. */
    public static EmployeePersonalResponse from(EmployeePersonal personal) {
        return new EmployeePersonalResponse(
                personal.getId(),
                personal.getTenantId(),
                personal.getEmployee().getId(),
                personal.getDateOfBirth(),
                personal.getMaritalStatus(),
                personal.getNationality(),
                personal.getEthnicity(),
                personal.getFatherName(),
                personal.getDifferentlyAbledType(),
                personal.isEligibleForFullTaxExemption(),
                personal.getCreatedAt(),
                personal.getUpdatedAt());
    }
}
