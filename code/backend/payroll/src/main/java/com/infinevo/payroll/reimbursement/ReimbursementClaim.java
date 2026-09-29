package com.infinevo.payroll.reimbursement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing an employee reimbursement claim in schema payroll (W-35.1).
 */
@Entity
@Table(name = "employee_reimbursement_request", schema = "payroll")
public class ReimbursementClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "reimbursement_id", nullable = false, updatable = false)
    private UUID reimbursementId;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(name = "bill_date", nullable = false, updatable = false)
    private LocalDate billDate;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "document_id")
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "approval_instance_id")
    private UUID approvalInstanceId;

    @Column(name = "pay_input_id")
    private UUID payInputId;

    @Column(name = "posted_period", length = 7)
    private String postedPeriod;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    protected ReimbursementClaim() {}

    public ReimbursementClaim(
            UUID tenantId,
            UUID employeeId,
            UUID reimbursementId,
            BigDecimal requestedAmount,
            LocalDate billDate,
            String description,
            UUID documentId,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.reimbursementId = Objects.requireNonNull(reimbursementId, "reimbursementId must not be null");
        this.requestedAmount = Objects.requireNonNull(requestedAmount, "requestedAmount must not be null");
        this.billDate = Objects.requireNonNull(billDate, "billDate must not be null");
        this.description = description;
        this.documentId = documentId;
        this.status = ClaimStatus.SUBMITTED;
        this.createdBy = actor != null ? actor : "system";
        this.updatedBy = actor != null ? actor : "system";
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (createdBy == null) {
            createdBy = "system";
        }
        if (updatedBy == null) {
            updatedBy = "system";
        }
        if (status == null) {
            status = ClaimStatus.SUBMITTED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getReimbursementId() {
        return reimbursementId;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public UUID getApprovalInstanceId() {
        return approvalInstanceId;
    }

    public void setApprovalInstanceId(UUID approvalInstanceId) {
        this.approvalInstanceId = approvalInstanceId;
    }

    public UUID getPayInputId() {
        return payInputId;
    }

    public void setPayInputId(UUID payInputId) {
        this.payInputId = payInputId;
    }

    public String getPostedPeriod() {
        return postedPeriod;
    }

    public void setPostedPeriod(String postedPeriod) {
        this.postedPeriod = postedPeriod;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
