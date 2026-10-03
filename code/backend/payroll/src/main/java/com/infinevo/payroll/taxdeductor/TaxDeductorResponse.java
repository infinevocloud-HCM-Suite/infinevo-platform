package com.infinevo.payroll.taxdeductor;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for tax deductor details (W-36.3).
 */
public record TaxDeductorResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("tan") String tan,
        @JsonProperty("pan") String pan,
        @JsonProperty("tds_circle") String tdsCircle,
        @JsonProperty("signatory_employee_id") UUID signatoryEmployeeId,
        @JsonProperty("signatory_name") String signatoryName,
        @JsonProperty("signatory_parent_name") String signatoryParentName,
        @JsonProperty("signatory_designation") String signatoryDesignation,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("updated_by") String updatedBy) {

    public static TaxDeductorResponse from(TaxDeductor entity) {
        return new TaxDeductorResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getTan() != null ? entity.getTan().trim() : null,
                entity.getPan() != null ? entity.getPan().trim() : null,
                entity.getTdsCircle(),
                entity.getSignatoryEmployeeId(),
                entity.getSignatoryName(),
                entity.getSignatoryParentName(),
                entity.getSignatoryDesignation(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
