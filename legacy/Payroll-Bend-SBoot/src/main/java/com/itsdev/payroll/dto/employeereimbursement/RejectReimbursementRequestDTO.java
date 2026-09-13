package com.itsdev.payroll.dto.employeereimbursement;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for rejecting an employee reimbursement request.
 */
public class RejectReimbursementRequestDTO {

    @NotBlank(message = "Rejection remarks/reason is required.")
    private String remarks;

    // ----------------------- Getters & Setters -----------------------

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
