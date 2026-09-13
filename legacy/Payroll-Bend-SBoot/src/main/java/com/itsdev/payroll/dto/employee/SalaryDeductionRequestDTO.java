package com.itsdev.payroll.dto.employee;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class SalaryDeductionRequestDTO {

    @NotBlank(message = "Employee ID is required")
    private String employeeId;

    @NotNull(message = "Deduction amount is required")
    @DecimalMin(value = "0.01", message = "Deduction amount must be greater than zero")
    private BigDecimal deductionAmount;

    @NotBlank(message = "Deduction month is required")
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Deduction month must be in YYYY-MM format")
    private String deductionMonth;

    private String deductionType;

    @NotBlank(message = "Reason is required")
    private String reason;

    private String remarks;

    private String proofUrl;
    private String proofPublicId;

    // =========================
    // Getters and Setters
    // =========================

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getDeductionType() {
        return deductionType;
    }

    public void setDeductionType(String deductionType) {
        this.deductionType = deductionType;
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
