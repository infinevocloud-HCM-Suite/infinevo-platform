package com.itsdev.payroll.dto.employee;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Grid-based deduction request. Accepts a list of independent deduction entries,
 * each with its own employeeId, type, amount, month, and reason.
 * Used by the grid UI where admin can add multiple employees, each with multiple
 * deduction types/rows in one submission.
 */
public class GridDeductionRequestDTO {

    @NotEmpty(message = "At least one deduction entry is required")
    private List<@Valid GridDeductionEntry> entries;

    public List<GridDeductionEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<GridDeductionEntry> entries) {
        this.entries = entries;
    }

    /**
     * A single row in the grid — one deduction for one employee.
     */
    public static class GridDeductionEntry {

        @NotBlank(message = "Employee ID is required")
        private String employeeId;

        @NotNull(message = "Deduction amount is required")
        @DecimalMin(value = "0.01", message = "Deduction amount must be greater than zero")
        private BigDecimal deductionAmount;

        @NotBlank(message = "Deduction month is required")
        @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Deduction month must be in YYYY-MM format")
        private String deductionMonth;

        @NotBlank(message = "Deduction type is required")
        private String deductionType;

        private String reason;

        private String remarks;

        private String proofUrl;
        private String proofPublicId;

        // Getters and Setters

        public String getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(String employeeId) {
            this.employeeId = employeeId;
        }

        public BigDecimal getDeductionAmount() {
            return deductionAmount;
        }

        public void setDeductionAmount(BigDecimal deductionAmount) {
            this.deductionAmount = deductionAmount;
        }

        public String getDeductionMonth() {
            return deductionMonth;
        }

        public void setDeductionMonth(String deductionMonth) {
            this.deductionMonth = deductionMonth;
        }

        public String getDeductionType() {
            return deductionType;
        }

        public void setDeductionType(String deductionType) {
            this.deductionType = deductionType;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getRemarks() {
            return remarks;
        }

        public void setRemarks(String remarks) {
            this.remarks = remarks;
        }

        public String getProofUrl() {
            return proofUrl;
        }

        public void setProofUrl(String proofUrl) {
            this.proofUrl = proofUrl;
        }

        public String getProofPublicId() {
            return proofPublicId;
        }

        public void setProofPublicId(String proofPublicId) {
            this.proofPublicId = proofPublicId;
        }
    }
}
