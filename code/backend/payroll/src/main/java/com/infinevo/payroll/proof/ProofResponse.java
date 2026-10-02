package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A proof of investment with its items (W-34.1 spec section 4).
 *
 * @param proofOpen whether the proof window is open today
 * @param editable whether the employee may change items and files now
 */
public record ProofResponse(
        UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("financial_year") String financialYear,
        ProofStatus status,
        @JsonProperty("proof_open") boolean proofOpen,
        boolean editable,
        @JsonProperty("due_date") LocalDate dueDate,
        @JsonProperty("attachment_mandatory") boolean attachmentMandatory,
        @JsonProperty("submitted_at") Instant submittedAt,
        @JsonProperty("decided_at") Instant decidedAt,
        @JsonProperty("reviewer_note") String reviewerNote,
        List<ProofItemResponse> items) {}
