package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.core.report.ExportFormat;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A company's bank file layout (W-88.2, {@code V224__bank_file_templates.sql}). */
@Entity
@Table(name = "bank_file_template", schema = "payroll")
public class BankFileTemplate {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 8)
    private ExportFormat fileFormat = ExportFormat.CSV;

    @Column(name = "include_header", nullable = false)
    private boolean includeHeader = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "columns", nullable = false)
    private List<BankFileColumn> columns = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected BankFileTemplate() {}

    public BankFileTemplate(UUID tenantId, String createdBy) {
        this.tenantId = tenantId;
        this.createdBy = createdBy == null || createdBy.isBlank() ? ACTOR_SYSTEM : createdBy;
        this.updatedBy = this.createdBy;
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

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ExportFormat getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(ExportFormat fileFormat) {
        this.fileFormat = fileFormat;
    }

    public boolean isIncludeHeader() {
        return includeHeader;
    }

    public void setIncludeHeader(boolean includeHeader) {
        this.includeHeader = includeHeader;
    }

    public List<BankFileColumn> getColumns() {
        return columns;
    }

    public void setColumns(List<BankFileColumn> columns) {
        this.columns = new ArrayList<>(columns);
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
