package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Review response returned to HR reviewer for proof verification (W-34.2 spec §4).
 */
public record ProofReviewResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("status") ProofStatus status,
        @JsonProperty("approval_instance_id") UUID approvalInstanceId,
        @JsonProperty("final_step_id") UUID finalStepId,
        @JsonProperty("submitted_at") Instant submittedAt,
        @JsonProperty("decided_at") Instant decidedAt,
        @JsonProperty("reviewer_note") String reviewerNote,
        @JsonProperty("comment_mandatory") boolean commentMandatory,
        @JsonProperty("items") List<ProofReviewItemResponse> items) {}
