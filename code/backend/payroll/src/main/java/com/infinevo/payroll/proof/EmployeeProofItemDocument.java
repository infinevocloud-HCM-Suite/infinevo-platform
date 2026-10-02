package com.infinevo.payroll.proof;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Links a proof item to a {@code core.document} row: the file itself is core's (W-34.1). */
@Entity
@Table(schema = "payroll", name = "employee_proof_item_document")
public class EmployeeProofItemDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    protected EmployeeProofItemDocument() {}

    public EmployeeProofItemDocument(UUID tenantId, UUID itemId, UUID documentId, String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.itemId = Objects.requireNonNull(itemId, "itemId must not be null");
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        this.createdBy = Objects.requireNonNull(actor, "actor must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
