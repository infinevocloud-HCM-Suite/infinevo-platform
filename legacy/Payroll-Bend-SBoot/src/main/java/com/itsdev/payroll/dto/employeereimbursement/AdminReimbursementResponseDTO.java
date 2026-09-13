package com.itsdev.payroll.dto.employeereimbursement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for Admin Reimbursement management.
 * Returns full details required for the Admin table, filter views, and review modal.
 */
public class AdminReimbursementResponseDTO {

    private Long id;
    private String employeeId;
    private String employeeNumber;
    private String employeeName;
    private String reimbursementType;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private String description;
    private LocalDate billDate;
    private String requestDate;
    private String reimbursementMonth;
    private String status;
    private String paymentStatus;
    private String remarks;
    // Multi-attachment support
    private List<String> attachmentUrls;
    private List<String> attachmentFileNames;
    // Legacy single-value fields (backward compatible)
    private String attachmentUrl;
    private String attachmentFileName;
    private String approvedBy;
    private LocalDateTime approvedAt;

    // ----------------------- Getters & Setters -----------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeNumber() { return employeeNumber; }
    public void setEmployeeNumber(String employeeNumber) { this.employeeNumber = employeeNumber; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getReimbursementType() { return reimbursementType; }
    public void setReimbursementType(String reimbursementType) { this.reimbursementType = reimbursementType; }

    /**
     * Alias getter for type (frontend compatibility)
     */
    public String getType() { return reimbursementType; }

    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }

    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getBillDate() { return billDate; }
    public void setBillDate(LocalDate billDate) { this.billDate = billDate; }

    public String getRequestDate() { return requestDate; }
    public void setRequestDate(String requestDate) { this.requestDate = requestDate; }

    public String getReimbursementMonth() { return reimbursementMonth; }
    public void setReimbursementMonth(String reimbursementMonth) { this.reimbursementMonth = reimbursementMonth; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public String getAttachmentFileName() { return attachmentFileName; }
    public void setAttachmentFileName(String attachmentFileName) { this.attachmentFileName = attachmentFileName; }

    public List<String> getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(List<String> attachmentUrls) { this.attachmentUrls = attachmentUrls; }

    public List<String> getAttachmentFileNames() { return attachmentFileNames; }
    public void setAttachmentFileNames(List<String> attachmentFileNames) { this.attachmentFileNames = attachmentFileNames; }

    /**
     * Alias getter for attachment (frontend compatibility)
     */
    public String getAttachment() { return attachmentUrl; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
}
