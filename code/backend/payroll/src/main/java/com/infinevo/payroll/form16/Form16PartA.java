package com.infinevo.payroll.form16;

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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * One employee's Form 16 Part A certificate for one financial year (W-36.5 §4, §6) — the link from
 * {@code (tenant, employee, fy)} to the {@code core.document} row that holds the PDF.
 *
 * <p>Superseded not edited, the W-36.1 shape: a re-upload marks this row {@code is_active = false} with
 * {@code superseded_at} set, and a new active row is inserted. {@code V109__form16_part_a.sql}.
 */
@Entity
@Table(
        name = "form16_part_a",
        schema = "payroll",
        indexes = {
            @Index(name = "idx_form16_part_a_tenant_fy", columnList = "tenant_id, financial_year, is_active"),
            @Index(name = "idx_form16_part_a_tenant_document", columnList = "tenant_id, document_id")
        })
@Audited
public class Form16PartA {

    /** {@code source_file_name} is {@code VARCHAR(255)}. */
    public static final int MAX_SOURCE_FILE_NAME = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "financial_year", nullable = false, length = 9, updatable = false)
    private String financialYear;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "source_file_name", nullable = false, length = MAX_SOURCE_FILE_NAME, updatable = false)
    private String sourceFileName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected Form16PartA() {}

    public Form16PartA(
            UUID tenantId,
            UUID employeeId,
            String financialYear,
            UUID documentId,
            String sourceFileName,
            String actor,
            Instant now) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.financialYear = Objects.requireNonNull(financialYear, "financialYear must not be null");
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        String name = Objects.requireNonNull(sourceFileName, "sourceFileName must not be null");
        this.sourceFileName = name.length() > MAX_SOURCE_FILE_NAME ? name.substring(0, MAX_SOURCE_FILE_NAME) : name;
        this.isActive = true;
        this.supersededAt = null;
        Instant timestamp = now != null ? now : Instant.now().truncatedTo(ChronoUnit.MICROS);
        String createdActor = actor != null ? actor : "system";
        this.createdAt = timestamp;
        this.createdBy = createdActor;
        this.updatedAt = timestamp;
        this.updatedBy = createdActor;
    }

    /** Marks this active row superseded by a re-upload. */
    public void supersede(Instant at, String actor) {
        this.isActive = false;
        this.supersededAt = Objects.requireNonNull(at, "at must not be null");
        this.updatedAt = at;
        this.updatedBy = Objects.requireNonNull(actor, "actor must not be null");
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.createdBy == null) {
            this.createdBy = "system";
        }
        if (this.updatedBy == null) {
            this.updatedBy = this.createdBy;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
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

    public String getFinancialYear() {
        return financialYear;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public boolean isActive() {
        return isActive;
    }

    public Instant getSupersededAt() {
        return supersededAt;
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
}
