package com.infinevo.payroll.form16;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Form 16 Part A certificate link per employee and financial year (W-36.5 §4, §6).
 * Table {@code payroll.form16_part_a}, migration {@code V109__form16_part_a.sql}.
 */
@Entity
@Audited
@Table(
        name = "form16_part_a",
        schema = "payroll",
        indexes = {
            @Index(name = "idx_form16_part_a_tenant_fy", columnList = "tenant_id, financial_year, is_active"),
            @Index(name = "idx_form16_part_a_tenant_document", columnList = "tenant_id, document_id")
        })
public class Form16PartA {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "financial_year", nullable = false, updatable = false, length = 9)
    private String financialYear;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "source_file_name", nullable = false, updatable = false, length = 255)
    private String sourceFileName;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public Form16PartA() {}

    public Form16PartA(
            UUID tenantId,
            UUID employeeId,
            String financialYear,
            UUID documentId,
            String sourceFileName,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.financialYear = Objects.requireNonNull(financialYear, "financialYear must not be null");
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        this.sourceFileName = Objects.requireNonNull(sourceFileName, "sourceFileName must not be null");
        this.isActive = true;
        this.supersededAt = null;
        this.createdBy = actor != null && !actor.isBlank() ? actor : "system";
        this.updatedBy = this.createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void supersede(String actor) {
        this.isActive = false;
        this.supersededAt = Instant.now();
        this.updatedAt = this.supersededAt;
        this.updatedBy = actor != null && !actor.isBlank() ? actor : "system";
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
