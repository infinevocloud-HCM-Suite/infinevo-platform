package com.infinevo.payroll.proof;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** One employee's proof of investment for a financial year, hanging off the declaration (W-34.1). */
@Entity
@Table(schema = "payroll", name = "employee_proof_of_investment")
public class EmployeeProofOfInvestment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProofStatus status = ProofStatus.DRAFT;

    /** Written by W-34.2 when the review starts. */
    @Column(name = "approval_instance_id")
    private UUID approvalInstanceId;

    /** The reason on a final return; written by W-34.2. */
    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    protected EmployeeProofOfInvestment() {}

    public EmployeeProofOfInvestment(
            UUID tenantId, UUID employeeId, UUID declarationId, String financialYear, String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.declarationId = Objects.requireNonNull(declarationId, "declarationId must not be null");
        this.financialYear = Objects.requireNonNull(financialYear, "financialYear must not be null");
        this.createdBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getDeclarationId() {
        return declarationId;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public ProofStatus getStatus() {
        return status;
    }

    public void setStatus(ProofStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public UUID getApprovalInstanceId() {
        return approvalInstanceId;
    }

    public void setApprovalInstanceId(UUID approvalInstanceId) {
        this.approvalInstanceId = approvalInstanceId;
    }

    public String getReviewerNote() {
        return reviewerNote;
    }

    public void setReviewerNote(String reviewerNote) {
        this.reviewerNote = reviewerNote;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
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
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy must not be null");
    }
}
