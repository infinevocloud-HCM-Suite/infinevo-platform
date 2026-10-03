package com.infinevo.payroll.form16;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Form 16 Part A response for employee self-service (W-36.5 §3, §4).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartAEmployeeResponse(
        @JsonProperty("document_id") UUID documentId,
        @JsonProperty("link") String link,
        @JsonProperty("expires_at") Instant expiresAt) {}
