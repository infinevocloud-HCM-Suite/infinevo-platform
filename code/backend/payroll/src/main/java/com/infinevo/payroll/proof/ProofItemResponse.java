package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** One proof item as the API returns it (W-34.1 spec section 4). */
public record ProofItemResponse(
        UUID id,
        @JsonProperty("source_kind") ProofSourceKind sourceKind,
        String description,
        @JsonProperty("declared_amount") BigDecimal declaredAmount,
        @JsonProperty("claimed_amount") BigDecimal claimedAmount,
        @JsonProperty("approved_amount") BigDecimal approvedAmount,
        ProofItemStatus status,
        @JsonProperty("employee_note") String employeeNote,
        @JsonProperty("reviewer_note") String reviewerNote,
        List<ProofDocumentResponse> documents) {

    static ProofItemResponse from(EmployeeProofItem item, List<ProofDocumentResponse> documents) {
        return new ProofItemResponse(
                item.getId(),
                item.getSourceKind(),
                item.getDescription(),
                item.getDeclaredAmount(),
                item.getClaimedAmount(),
                item.getApprovedAmount(),
                item.getStatus(),
                item.getEmployeeNote(),
                item.getReviewerNote(),
                documents);
    }
}
