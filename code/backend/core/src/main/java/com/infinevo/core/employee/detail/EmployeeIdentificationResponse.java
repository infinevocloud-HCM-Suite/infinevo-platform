package com.infinevo.core.employee.detail;

import java.time.Instant;
import java.util.UUID;

/**
 * An employee's identity documents as the API returns it (W-13.2, spec section 4).
 *
 * <p>The numbers are returned in full. Redaction applies to {@code core.audit_log} — where a value
 * is kept indefinitely and read by people who are not the employee's administrator — and not to the
 * record itself, which is the data the section exists to hold. Who may call at all is settled by the
 * filter chain; column-level encryption was considered and deferred to W-57 (spec section 13,
 * decision 1).
 */
public record EmployeeIdentificationResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        String immigrationStatus,
        String aadhaarNumber,
        String panNumber,
        String personalTaxId,
        String socialInsuranceNumber,
        String idProofType,
        String idDocumentName,
        String idDocumentNumber,
        String addressProofType,
        String addressDocumentName,
        String addressDocumentNumber,
        Instant createdAt,
        Instant updatedAt) {

    /** Repacks a persisted section. The only way one of these is built. */
    public static EmployeeIdentificationResponse from(EmployeeIdentification identification) {
        return new EmployeeIdentificationResponse(
                identification.getId(),
                identification.getTenantId(),
                identification.getEmployee().getId(),
                identification.getImmigrationStatus(),
                identification.getAadhaarNumber(),
                identification.getPanNumber(),
                identification.getPersonalTaxId(),
                identification.getSocialInsuranceNumber(),
                identification.getIdProofType(),
                identification.getIdDocumentName(),
                identification.getIdDocumentNumber(),
                identification.getAddressProofType(),
                identification.getAddressDocumentName(),
                identification.getAddressDocumentNumber(),
                identification.getCreatedAt(),
                identification.getUpdatedAt());
    }
}
