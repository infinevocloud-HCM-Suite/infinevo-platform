package com.itsdev.payroll.dto.employeereimbursement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for employee reimbursement request data.
 */
public class EmployeeReimbursementResponseDTO {

    private Long id;
    private String employeeNumber;
    private String requestDate;
    private String reimbursementType;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private LocalDate billDate;
    private String description;
    // Multi-attachment support: JSON-array backed lists
    private List<String> attachmentUrls;
    private List<String> attachmentFileNames;
    // Legacy single-value fields (kept for backward compatibility)
    private String attachmentUrl;
    private String attachmentFileName;
    private String status;
    private String remarks;
    private String paymentStatus;
    private String reimbursementMonth;

    // ----------------------- Getters & Setters -----------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeNumber() { return employeeNumber; }
    public void setEmployeeNumber(String employeeNumber) { this.employeeNumber = employeeNumber; }

    public String getRequestDate() { return requestDate; }
    public void setRequestDate(String requestDate) { this.requestDate = requestDate; }

    public String getReimbursementType() { return reimbursementType; }
    public void setReimbursementType(String reimbursementType) { this.reimbursementType = reimbursementType; }

    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }

    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }

    public LocalDate getBillDate() { return billDate; }
    public void setBillDate(LocalDate billDate) { this.billDate = billDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public String getAttachmentFileName() { return attachmentFileName; }
    public void setAttachmentFileName(String attachmentFileName) { this.attachmentFileName = attachmentFileName; }

    public List<String> getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(List<String> attachmentUrls) { this.attachmentUrls = attachmentUrls; }

    public List<String> getAttachmentFileNames() { return attachmentFileNames; }
    public void setAttachmentFileNames(List<String> attachmentFileNames) { this.attachmentFileNames = attachmentFileNames; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getReimbursementMonth() { return reimbursementMonth; }
    public void setReimbursementMonth(String reimbursementMonth) { this.reimbursementMonth = reimbursementMonth; }
}
