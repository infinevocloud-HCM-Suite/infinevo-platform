package com.infinevo.core.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * One stored file (W-21) — {@code core.document},
 * {@code migration/src/main/resources/db/migration/core/V037__document.sql}.
 *
 * <p><strong>A pointer, never a URL.</strong> The row holds a container and a path. Every legacy
 * table this replaces held a full Cloudinary URL, and a URL is a credential: holding it was enough
 * to download the file, with no session and no tenant check (spec section 1). A link to this file is
 * issued per request by {@link DocumentLinkService}, signed and expiring.
 *
 * <p><strong>The id is assigned, not generated.</strong> The blob path names the document id, and
 * the blob is written before the row (spec section 4), so the id exists before either. That makes
 * this a {@link Persistable}: with an assigned id Spring Data cannot tell a new row from an existing
 * one, and would issue a {@code SELECT} followed by a merge on every insert. {@link #isNew} answers
 * it directly.
 *
 * <p><strong>{@code employeeId} is a plain column, not an association</strong>, as
 * {@code RoleAction} does it. Nothing here needs the employee loaded, and the foreign key does not
 * stop a cross-tenant reference anyway — PostgreSQL checks it as the table owner, with row security
 * off — so {@link DocumentServiceImpl} looks the employee up in the bound tenant before storing.
 *
 * <p>Deletion is soft (spec section 13, decision 2): the flag is set and the blob is kept.
 * {@code app_user} holds no {@code DELETE} on the table at all ({@code V037}).
 */
@Entity
@Table(
        name = "document",
        schema = "core",
        indexes = {
            @Index(name = "idx_document_tenant_employee_kind", columnList = "tenant_id, employee_id, kind"),
            @Index(name = "uk_document_tenant_blob_path", columnList = "tenant_id, blob_path", unique = true)
        })
public class Document implements Persistable<UUID> {

    /** Written into {@code created_by} / {@code updated_by} when no authenticated user is on the thread. */
    public static final String ACTOR_SYSTEM = "system";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Never taken from a request. The service reads it from {@code TenantContext}. */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Null for a document that belongs to the tenant rather than to a person — an export. */
    @Column(name = "employee_id", updatable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32, updatable = false)
    private DocumentKind kind;

    @Column(name = "file_name", nullable = false, length = 255, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 128, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "blob_container", nullable = false, length = 64, updatable = false)
    private String blobContainer;

    @Column(name = "blob_path", nullable = false, length = 512, updatable = false)
    private String blobPath;

    @Column(name = "checksum_sha256", nullable = false, length = 64, columnDefinition = "char(64)", updatable = false)
    private String checksumSha256;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    /** True until the row has been persisted or loaded — see the class comment. */
    @Transient
    private boolean isNew = true;

    protected Document() {}

    Document(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            DocumentKind kind,
            String fileName,
            String contentType,
            long sizeBytes,
            String blobContainer,
            String blobPath,
            String checksumSha256,
            String actor) {
        this.id = id;
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.kind = kind;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.blobContainer = blobContainer;
        this.blobPath = blobPath;
        this.checksumSha256 = checksumSha256;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getBlobContainer() {
        return blobContainer;
    }

    public String getBlobPath() {
        return blobPath;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public boolean isDeleted() {
        return deleted;
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

    /**
     * Soft delete — the row stays, hidden from every read path, and the blob is kept.
     *
     * <p>Package-private: only {@link DocumentServiceImpl} calls it, after resolving the document in
     * the bound tenant.
     */
    void markDeleted(String actor) {
        this.deleted = true;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }
}
