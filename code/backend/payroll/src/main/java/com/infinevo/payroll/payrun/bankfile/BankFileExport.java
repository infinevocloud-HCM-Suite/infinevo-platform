package com.infinevo.payroll.payrun.bankfile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One bank file made for a pay run, kept in the document store (W-88.2, {@code V224}). */
@Entity
@Table(name = "bank_file_export", schema = "payroll")
public class BankFileExport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "payrun_id", nullable = false, updatable = false)
    private UUID payrunId;

    /** Null for the built-in template, or once the template is deleted. */
    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "template_name", nullable = false, length = 80, updatable = false)
    private String templateName;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "file_name", nullable = false, length = 200, updatable = false)
    private String fileName;

    @Column(name = "row_count", nullable = false, updatable = false)
    private int rowCount;

    @Column(name = "file_total", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal fileTotal;

    /** How many employees were ticked; null when the file covers everyone in the run. */
    @Column(name = "selected_employee_count", updatable = false)
    private Integer selectedEmployeeCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    protected BankFileExport() {}

    public BankFileExport(
            UUID tenantId,
            UUID payrunId,
            UUID templateId,
            String templateName,
            UUID documentId,
            String fileName,
            int rowCount,
            BigDecimal fileTotal,
            Integer selectedEmployeeCount,
            String createdBy) {
        this.tenantId = tenantId;
        this.payrunId = payrunId;
        this.templateId = templateId;
        this.templateName = templateName;
        this.documentId = documentId;
        this.fileName = fileName;
        this.rowCount = rowCount;
        this.fileTotal = fileTotal;
        this.selectedEmployeeCount = selectedEmployeeCount;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPayrunId() {
        return payrunId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getFileName() {
        return fileName;
    }

    public int getRowCount() {
        return rowCount;
    }

    public BigDecimal getFileTotal() {
        return fileTotal;
    }

    public Integer getSelectedEmployeeCount() {
        return selectedEmployeeCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
