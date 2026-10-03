package com.infinevo.payroll.form16;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Item in the officer list of uploaded Form 16 Part A certificates for a financial year (W-36.5 §3, §4).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartAOfficerItem(
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("document_id") UUID documentId,
        @JsonProperty("uploaded_at") Instant uploadedAt) {}
