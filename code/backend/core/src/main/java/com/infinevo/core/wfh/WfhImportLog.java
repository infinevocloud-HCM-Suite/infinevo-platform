package com.infinevo.core.wfh;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit log recording bulk CSV imports of WFH quota allocations (W-74).
 */
@Entity
@Table(
        name = "wfh_import_log",
        schema = "core",
        indexes = {
            @Index(name = "idx_wfh_import_log_tenant_started", columnList = "tenant_id, started_at DESC"),
            @Index(name = "idx_wfh_import_log_tenant_status", columnList = "tenant_id, status")
        })
public class WfhImportLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    @Column(name = "error_document_id")
    private UUID errorDocumentId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "wfh_year", nullable = false, length = 9)
    private String wfhYear;

    @Column(name = "status", nullable = false, length = 24)
    private String status = "PENDING"; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(name = "is_dry_run", nullable = false)
    private boolean dryRun = false;

    @Column(name = "rows_total", nullable = false)
    private int rowsTotal = 0;

    @Column(name = "rows_imported", nullable = false)
    private int rowsImported = 0;

    @Column(name = "rows_failed", nullable = false)
    private int rowsFailed = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "started_at", nullable = false)
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

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (startedAt == null) {
            startedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getWfhYear() {
        return wfhYear;
    }

    public void setWfhYear(String wfhYear) {
        this.wfhYear = wfhYear;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
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

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WfhImportLog that = (WfhImportLog) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
