package com.infinevo.core.leave;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing an attachment to a leave request (W-16.3).
 */
@Entity
@Audited
@Table(
        name = "leave_request_document",
        schema = "core",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_leave_request_document",
                    columnNames = {"tenant_id", "leave_request_id", "document_id"})
        },
        indexes = {@Index(name = "idx_leave_request_document_lookup", columnList = "tenant_id, leave_request_id")})
public class LeaveRequestDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "leave_request_id", nullable = false)
    private UUID leaveRequestId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    public LeaveRequestDocument() {}

    public LeaveRequestDocument(UUID tenantId, UUID leaveRequestId, UUID documentId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.leaveRequestId = Objects.requireNonNull(leaveRequestId, "leaveRequestId must not be null");
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
    }

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (createdBy == null) {
            createdBy = "system";
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (updatedBy == null) {
            updatedBy = "system";
        }
    }

    @PreUpdate
    public void onPreUpdate() {
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

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getLeaveRequestId() {
        return leaveRequestId;
    }

    public void setLeaveRequestId(UUID leaveRequestId) {
        this.leaveRequestId = leaveRequestId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
