package com.itsdev.payroll.entity.employeereimbursement;

import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementPaymentStatus;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementStatus;
import com.itsdev.payroll.enumeration.employeereimbursement.ReimbursementType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity representing an employee's reimbursement request.
 * Table: employee_reimbursement_request
 *
 * Business rules (enforced at service layer):
 *  - status is always set to PENDING on creation
 *  - approvedAmount, remarks, approvedBy, approvedAt are always null on creation
 *  - paymentStatus is always set to UNPAID on creation
 *  - employeeId and organizationId come from JWT / request header — never from request body
 *  - attachment files are stored in Cloudinary; only URLs, public IDs, and filenames are stored here.
 */
@Entity
@Table(name = "employee_reimbursement_request")
public class EmployeeReimbursementRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reimbursement_type", nullable = false, length = 20)
    private ReimbursementType reimbursementType;

    @Column(name = "requested_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "attachment_url", length = 2000)
    private String attachmentUrl;

    @Column(name = "attachment_public_id", length = 1000)
    private String attachmentPublicId;

    @Column(name = "attachment_file_name", length = 1000)
    private String attachmentFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReimbursementStatus status;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private ReimbursementPaymentStatus paymentStatus;

    @Column(name = "reimbursement_month", length = 20)
    private String reimbursementMonth;

    /**
     * Reserved for future payrun integration.
     * Populated when the reimbursement is included in a payrun.
     */
    @Column(name = "payrun_id", length = 100)
    private String payrunId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "approved_by", length = 200)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ----------------------- Getters & Setters -----------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public ReimbursementType getReimbursementType() { return reimbursementType; }
    public void setReimbursementType(ReimbursementType reimbursementType) { this.reimbursementType = reimbursementType; }

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

    public String getAttachmentPublicId() { return attachmentPublicId; }
    public void setAttachmentPublicId(String attachmentPublicId) { this.attachmentPublicId = attachmentPublicId; }

    public String getAttachmentFileName() { return attachmentFileName; }
    public void setAttachmentFileName(String attachmentFileName) { this.attachmentFileName = attachmentFileName; }

    public ReimbursementStatus getStatus() { return status; }
    public void setStatus(ReimbursementStatus status) { this.status = status; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public ReimbursementPaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(ReimbursementPaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getReimbursementMonth() { return reimbursementMonth; }
    public void setReimbursementMonth(String reimbursementMonth) { this.reimbursementMonth = reimbursementMonth; }

    public String getPayrunId() { return payrunId; }
    public void setPayrunId(String payrunId) { this.payrunId = payrunId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
}
