package com.infinevo.payroll.deduction;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One deduction as the API returns it (W-35.2 §4); {@code posted_period} is what the ledger took. */
public record EmployeeDeductionResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("employee_name") String employeeName,
        @JsonProperty("period") String period,
        @JsonProperty("deduction_type") DeductionType deductionType,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("reason") String reason,
        @JsonProperty("remarks") String remarks,
        @JsonProperty("document_id") UUID documentId,
        @JsonProperty("status") DeductionState status,
        @JsonProperty("pay_input_id") UUID payInputId,
        @JsonProperty("posted_period") String postedPeriod,
        @JsonProperty("reversal_pay_input_id") UUID reversalPayInputId,
        @JsonProperty("reversed_at") Instant reversedAt,
        @JsonProperty("reversed_by") UUID reversedBy,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("created_by") String createdBy) {

    /** {@code employeeName} comes from {@code EmployeeService.displayNames} (W-47.4 §4); null when unresolved. */
    static EmployeeDeductionResponse from(EmployeeDeduction d, String employeeName) {
        return new EmployeeDeductionResponse(
                d.getId(),
                d.getEmployeeId(),
                employeeName,
                d.getPeriod().toString(),
                d.getDeductionType(),
                d.getAmount(),
                d.getReason(),
                d.getRemarks(),
                d.getDocumentId(),
                d.getStatus(),
                d.getPayInputId(),
                d.getPostedPeriod().toString(),
                d.getReversalPayInputId(),
                d.getReversedAt(),
                d.getReversedBy(),
                d.getCreatedAt(),
                d.getCreatedBy());
    }
}
