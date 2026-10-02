package com.infinevo.core.leave;

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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity tracking a bulk leave import execution run (W-16.4b, spec section 4 &amp; 6).
 *
 * <p>Unlike append-only ledger tables, this entity receives updates during processing:
 * counts, status, error document reference, and finish timestamp are updated as rows are processed.
 */
@Entity
@Table(name = "leave_import_log", schema = "core")
public class LeaveImportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "source_document_id", nullable = false, updatable = false)
    private UUID sourceDocumentId;

    @Column(name = "error_document_id")
    private UUID errorDocumentId;

    @Column(name = "leave_year", nullable = false, length = 9, updatable = false)
    private String leaveYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ImportStatus status = ImportStatus.PENDING;

    @Column(name = "is_dry_run", nullable = false, updatable = false)
    private boolean isDryRun;

    @Column(name = "rows_total", nullable = false)
    private int rowsTotal;

    @Column(name = "rows_imported", nullable = false)
    private int rowsImported;

    @Column(name = "rows_failed", nullable = false)
    private int rowsFailed;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public LeaveImportLog() {}

    public LeaveImportLog(UUID tenantId, UUID sourceDocumentId, String leaveYear, boolean isDryRun, int rowsTotal) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.sourceDocumentId = Objects.requireNonNull(sourceDocumentId, "sourceDocumentId must not be null");
        this.leaveYear = Objects.requireNonNull(leaveYear, "leaveYear must not be null");
        this.isDryRun = isDryRun;
        this.rowsTotal = rowsTotal;
        this.status = ImportStatus.PENDING;
        this.startedAt = Instant.now();
    }

    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (startedAt == null) {
            startedAt = now;
        }
    }

    @PreUpdate
    void onPreUpdate() {
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

    public UUID getSourceDocumentId() {
        return sourceDocumentId;
    }

    public void setSourceDocumentId(UUID sourceDocumentId) {
        this.sourceDocumentId = sourceDocumentId;
    }

    public UUID getErrorDocumentId() {
        return errorDocumentId;
    }

    public void setErrorDocumentId(UUID errorDocumentId) {
        this.errorDocumentId = errorDocumentId;
    }

    public String getLeaveYear() {
        return leaveYear;
    }

    public void setLeaveYear(String leaveYear) {
        this.leaveYear = leaveYear;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public void setStatus(ImportStatus status) {
        this.status = status;
    }

    public boolean isDryRun() {
        return isDryRun;
    }

    public void setDryRun(boolean dryRun) {
        isDryRun = dryRun;
    }

    public int getRowsTotal() {
        return rowsTotal;
    }

    public void setRowsTotal(int rowsTotal) {
        this.rowsTotal = rowsTotal;
    }

    public int getRowsImported() {
        return rowsImported;
    }

    public void setRowsImported(int rowsImported) {
        this.rowsImported = rowsImported;
    }

    public int getRowsFailed() {
        return rowsFailed;
    }

    public void setRowsFailed(int rowsFailed) {
        this.rowsFailed = rowsFailed;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
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
