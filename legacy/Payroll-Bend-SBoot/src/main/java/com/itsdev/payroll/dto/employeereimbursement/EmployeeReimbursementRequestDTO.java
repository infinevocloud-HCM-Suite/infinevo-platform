package com.itsdev.payroll.dto.employeereimbursement;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for creating an employee reimbursement request.
 *
 * Fields NOT accepted here (set automatically by service):
 *   - employeeId (comes from JWT)
 *   - organizationId (comes from request header)
 *   - status (always PENDING)
 *   - approvedAmount (always null)
 *   - remarks (always null)
 *   - paymentStatus (always UNPAID)
 *   - approvedBy / approvedAt (always null)
 */
public class EmployeeReimbursementRequestDTO {

    @NotBlank(message = "Reimbursement type is required.")
    private String reimbursementType;

    @NotNull(message = "Requested amount is required.")
    @Positive(message = "Requested amount must be greater than 0.")
    private BigDecimal requestedAmount;

    @NotNull(message = "Bill date is required.")
    @PastOrPresent(message = "Bill date cannot be a future date.")
    private LocalDate billDate;

    @NotBlank(message = "Description is required.")
    @Size(max = 1000, message = "Description must not exceed 1000 characters.")
    private String description;

    private String attachmentUrl;

    // ----------------------- Getters & Setters -----------------------

    public String getReimbursementType() { return reimbursementType; }
    public void setReimbursementType(String reimbursementType) { this.reimbursementType = reimbursementType; }

    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }

    public LocalDate getBillDate() { return billDate; }
    public void setBillDate(LocalDate billDate) { this.billDate = billDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }
}
