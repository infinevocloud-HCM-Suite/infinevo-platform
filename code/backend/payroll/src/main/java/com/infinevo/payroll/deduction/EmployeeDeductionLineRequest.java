package com.infinevo.payroll.deduction;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of {@code POST /api/v1/payroll/employee-deductions} (W-35.2 §4). {@code period} and
 * {@code deductionType} arrive as text and are parsed by {@link EmployeeDeductionRules}, so a bad value
 * is a {@code 400} that names its line rather than an unreadable body.
 */
public record EmployeeDeductionLineRequest(
        @JsonProperty("employee_id") @JsonAlias("employeeId") UUID employeeId,
        @JsonProperty("period") String period,
        @JsonProperty("deduction_type") @JsonAlias("deductionType") String deductionType,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("reason") String reason,
        @JsonProperty("remarks") String remarks,
        @JsonProperty("document_id") @JsonAlias("documentId") UUID documentId) {}
