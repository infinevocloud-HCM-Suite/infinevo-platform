package com.itsdev.payroll.dto.employeereimbursement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Request DTO for approving an employee reimbursement request.
 */
public class ApproveReimbursementRequestDTO {

    @NotNull(message = "Approved amount is required.")
    @Positive(message = "Approved amount must be greater than 0.")
    private BigDecimal approvedAmount;

    private String remarks;

    private String reimbursementMonth;

    // ----------------------- Getters & Setters -----------------------

    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getReimbursementMonth() { return reimbursementMonth; }
    public void setReimbursementMonth(String reimbursementMonth) { this.reimbursementMonth = reimbursementMonth; }
}
