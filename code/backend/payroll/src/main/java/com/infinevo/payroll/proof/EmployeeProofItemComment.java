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

/**
 * A comment in the flat review conversation on a proof-of-investment item (W-34.2).
 *
 * <p>Append-only and immutable. It is the permanent record of the interaction between
 * the employee and the reviewer during verification.
 */
@Entity
@Table(schema = "payroll", name = "employee_proof_item_comment")
public class EmployeeProofItemComment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "author_employee_id", nullable = false)
    private UUID authorEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 8)
    private ProofCommentRole authorRole;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    protected EmployeeProofItemComment() {}

    public EmployeeProofItemComment(
            UUID tenantId, UUID itemId, UUID authorEmployeeId, ProofCommentRole authorRole, String body, String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.itemId = Objects.requireNonNull(itemId, "itemId must not be null");
        this.authorEmployeeId = Objects.requireNonNull(authorEmployeeId, "authorEmployeeId must not be null");
        this.authorRole = Objects.requireNonNull(authorRole, "authorRole must not be null");
        this.body = Objects.requireNonNull(body, "body must not be null");
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

    public UUID getAuthorEmployeeId() {
        return authorEmployeeId;
    }

    public ProofCommentRole getAuthorRole() {
        return authorRole;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
