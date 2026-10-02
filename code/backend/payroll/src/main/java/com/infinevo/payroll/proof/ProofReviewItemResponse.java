package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Item details returned within {@link ProofReviewResponse} for reviewer evaluation (W-34.2 spec §4).
 */
public record ProofReviewItemResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("source_kind") ProofSourceKind sourceKind,
        @JsonProperty("description") String description,
        @JsonProperty("declared_amount") BigDecimal declaredAmount,
        @JsonProperty("claimed_amount") BigDecimal claimedAmount,
        @JsonProperty("approved_amount") BigDecimal approvedAmount,
        @JsonProperty("status") ProofItemStatus status,
        @JsonProperty("employee_note") String employeeNote,
        @JsonProperty("reviewer_note") String reviewerNote,
        @JsonProperty("documents") List<ProofDocumentResponse> documents,
        @JsonProperty("open_step_id") UUID openStepId,
        @JsonProperty("comment_count") long commentCount) {

    public static ProofReviewItemResponse from(
            EmployeeProofItem item, List<ProofDocumentResponse> documents, UUID openStepId, long commentCount) {
        return new ProofReviewItemResponse(
                item.getId(),
                item.getSourceKind(),
                item.getDescription(),
                item.getDeclaredAmount(),
                item.getClaimedAmount(),
                item.getApprovedAmount(),
                item.getStatus(),
                item.getEmployeeNote(),
                item.getReviewerNote(),
                documents,
                openStepId,
                commentCount);
    }
}
