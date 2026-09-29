package com.infinevo.worker.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing an audit retention sweep run (W-22.2).
 *
 * <p>Persisted in {@code core.retention_run} to provide an accountable record of data retention
 * deletions per tenant and target table. {@code core.retention_run} is itself never swept.
 */
@Entity
@Table(name = "retention_run", schema = "core")
public class RetentionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "target_table", nullable = false, length = 64)
    private String targetTable;

    @Column(name = "cutoff_date", nullable = false)
    private LocalDate cutoffDate;

    @Column(name = "rows_deleted", nullable = false)
    private long rowsDeleted;

    @Column(name = "is_dry_run", nullable = false)
    private boolean isDryRun;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    protected RetentionRun() {}

    public RetentionRun(
            UUID tenantId,
            String targetTable,
            LocalDate cutoffDate,
            long rowsDeleted,
            boolean isDryRun,
            String status,
            Instant startedAt,
            Instant finishedAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.targetTable = Objects.requireNonNull(targetTable, "targetTable must not be null");
        this.cutoffDate = Objects.requireNonNull(cutoffDate, "cutoffDate must not be null");
        this.rowsDeleted = rowsDeleted;
        this.isDryRun = isDryRun;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt must not be null");
        this.finishedAt = finishedAt;
        this.createdAt = startedAt != null ? startedAt : Instant.now();
        this.updatedAt = startedAt != null ? startedAt : Instant.now();
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

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public LocalDate getCutoffDate() {
        return cutoffDate;
    }

    public void setCutoffDate(LocalDate cutoffDate) {
        this.cutoffDate = cutoffDate;
    }

    public long getRowsDeleted() {
        return rowsDeleted;
    }

    public void setRowsDeleted(long rowsDeleted) {
        this.rowsDeleted = rowsDeleted;
    }

    public boolean isDryRun() {
        return isDryRun;
    }

    public void setDryRun(boolean dryRun) {
        isDryRun = dryRun;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}
