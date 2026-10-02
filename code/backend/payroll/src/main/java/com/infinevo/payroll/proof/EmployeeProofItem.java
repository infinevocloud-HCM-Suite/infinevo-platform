package com.infinevo.payroll.proof;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** One declared line that needs proof, with what the employee claims and what the reviewer allows (W-34.1). */
@Entity
@Table(schema = "payroll", name = "employee_proof_item")
public class EmployeeProofItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "proof_id", nullable = false)
    private UUID proofId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 24)
    private ProofSourceKind sourceKind;

    /** The declared line this item stands for. Polymorphic by the source kind, so no foreign key. */
    @Column(name = "source_line_id", nullable = false)
    private UUID sourceLineId;

    @Column(name = "description", nullable = false, length = 150)
    private String description;

    @Column(name = "declared_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal declaredAmount = BigDecimal.ZERO;

    @Column(name = "claimed_amount", precision = 19, scale = 4)
    private BigDecimal claimedAmount;

    /** Written by W-34.2 only. */
    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProofItemStatus status = ProofItemStatus.PENDING;

    @Column(name = "employee_note", length = 1000)
    private String employeeNote;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

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

    protected EmployeeProofItem() {}

    public EmployeeProofItem(
            UUID tenantId,
            UUID proofId,
            ProofSourceKind sourceKind,
            UUID sourceLineId,
            String description,
            BigDecimal declaredAmount,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.proofId = Objects.requireNonNull(proofId, "proofId must not be null");
        this.sourceKind = Objects.requireNonNull(sourceKind, "sourceKind must not be null");
        this.sourceLineId = Objects.requireNonNull(sourceLineId, "sourceLineId must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.declaredAmount = Objects.requireNonNull(declaredAmount, "declaredAmount must not be null");
        this.createdBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProofId() {
        return proofId;
    }

    public ProofSourceKind getSourceKind() {
        return sourceKind;
    }

    public UUID getSourceLineId() {
        return sourceLineId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    public BigDecimal getDeclaredAmount() {
        return declaredAmount;
    }

    public void setDeclaredAmount(BigDecimal declaredAmount) {
        this.declaredAmount = Objects.requireNonNull(declaredAmount, "declaredAmount must not be null");
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public void setClaimedAmount(BigDecimal claimedAmount) {
        this.claimedAmount = claimedAmount;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public ProofItemStatus getStatus() {
        return status;
    }

    public void setStatus(ProofItemStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public String getEmployeeNote() {
        return employeeNote;
    }

    public void setEmployeeNote(String employeeNote) {
        this.employeeNote = employeeNote;
    }

    public String getReviewerNote() {
        return reviewerNote;
    }

    public void setReviewerNote(String reviewerNote) {
        this.reviewerNote = reviewerNote;
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
