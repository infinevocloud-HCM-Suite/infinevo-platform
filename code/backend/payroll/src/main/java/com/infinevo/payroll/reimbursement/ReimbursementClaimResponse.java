package com.infinevo.payroll.reimbursement;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.component.Reimbursement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API response representing an employee reimbursement claim (W-35.1).
 */
public record ReimbursementClaimResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("reimbursement_id") UUID reimbursementId,
        @JsonProperty("component_code") String componentCode,
        @JsonProperty("component_name") String componentName,
        @JsonProperty("max_limit") BigDecimal maxLimit,
        @JsonProperty("requested_amount") BigDecimal requestedAmount,
        @JsonProperty("approved_amount") BigDecimal approvedAmount,
        @JsonProperty("bill_date") LocalDate billDate,
        @JsonProperty("description") String description,
        @JsonProperty("document_id") UUID documentId,
        @JsonProperty("status") ClaimStatus status,
        @JsonProperty("remarks") String remarks,
        @JsonProperty("approval_instance_id") UUID approvalInstanceId,
        @JsonProperty("pay_input_id") UUID payInputId,
        @JsonProperty("posted_period") String postedPeriod,
        @JsonProperty("approved_by") UUID approvedBy,
        @JsonProperty("approved_at") Instant approvedAt,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {

    public static ReimbursementClaimResponse from(
            ReimbursementClaim claim, String componentCode, String componentName, BigDecimal maxLimit) {
        return new ReimbursementClaimResponse(
                claim.getId(),
                claim.getEmployeeId(),
                claim.getReimbursementId(),
                componentCode,
                componentName,
                maxLimit,
                claim.getRequestedAmount(),
                claim.getApprovedAmount(),
                claim.getBillDate(),
                claim.getDescription(),
                claim.getDocumentId(),
                claim.getStatus(),
                claim.getRemarks(),
                claim.getApprovalInstanceId(),
                claim.getPayInputId(),
                claim.getPostedPeriod(),
                claim.getApprovedBy(),
                claim.getApprovedAt(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    public static ReimbursementClaimResponse from(ReimbursementClaim claim, Reimbursement component) {
        String code = component != null ? component.getCode() : null;
        String name = component != null ? component.getName() : null;
        BigDecimal maxLimit = component != null ? component.getMaxLimit() : null;
        return from(claim, code, name, maxLimit);
    }
}
