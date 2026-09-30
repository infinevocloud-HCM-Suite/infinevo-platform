package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One employee row of a run (W-29.1 §4), with its five totals once computed (W-29.2 §4) and the
 * day figures behind loss of pay (W-29.3 §4); {@code net_pay} may be negative.
 * {@code employee_number} is read from {@code core.employee} through {@code EmployeeService}, not
 * stored on the row (W-29.1 §13 decision 7); it is null only when the employee has since been
 * soft-deleted.
 */
public record EmployeePayRunResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("employee_number") String employeeNumber,
        @JsonProperty("inclusion_status") InclusionStatus inclusionStatus,
        @JsonProperty("skip_reason") SkipReason skipReason,
        @JsonProperty("salary_version_id") UUID salaryVersionId,
        @JsonProperty("gross_earnings") BigDecimal grossEarnings,
        @JsonProperty("total_reimbursements") BigDecimal totalReimbursements,
        @JsonProperty("total_benefits") BigDecimal totalBenefits,
        @JsonProperty("total_deductions") BigDecimal totalDeductions,
        @JsonProperty("net_pay") BigDecimal netPay,
        @JsonProperty("lop_days") BigDecimal lopDays,
        @JsonProperty("unpaid_days") BigDecimal unpaidDays,
        @JsonProperty("paid_days") BigDecimal paidDays,
        @JsonProperty("unpriced_input_count") int unpricedInputCount,
        @JsonProperty("computed_at") Instant computedAt,
        @JsonProperty("computation_error") String computationError) {

    public static EmployeePayRunResponse from(EmployeePayRun row, String employeeNumber) {
        return new EmployeePayRunResponse(
                row.getId(),
                row.getEmployeeId(),
                employeeNumber,
                row.getInclusionStatus(),
                row.getSkipReason(),
                row.getSalaryVersionId(),
                row.getGrossEarnings(),
                row.getTotalReimbursements(),
                row.getTotalBenefits(),
                row.getTotalDeductions(),
                row.getNetPay(),
                row.getLopDays(),
                row.getUnpaidDays(),
                row.getPaidDays(),
                row.getUnpricedInputCount(),
                row.getComputedAt(),
                row.getComputationError());
    }
}
