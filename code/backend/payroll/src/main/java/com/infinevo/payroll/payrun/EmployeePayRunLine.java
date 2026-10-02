package com.infinevo.payroll.payrun;

import com.infinevo.shared.audit.Audited;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * One component on one employee's run (W-29.2 §6). The code and name are snapshots taken at compute
 * time, so a catalogue rename never rewrites a paid month; {@code component_id} has no foreign key for
 * the same reason. Lines are written once and replaced wholesale by a recompute, never edited.
 */
@Entity
@Table(name = "employee_payrun_line", schema = "payroll")
@Audited
public class EmployeePayRunLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_payrun_id", nullable = false, updatable = false)
    private UUID employeePayrunId;

    @Column(name = "payrun_id", nullable = false, updatable = false)
    private UUID payrunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_kind", nullable = false, length = 16, updatable = false)
    private LineKind lineKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private LineSource source;

    @Column(name = "component_id", updatable = false)
    private UUID componentId;

    @Column(name = "component_code", nullable = false, length = 64, updatable = false)
    private String componentCode;

    @Column(name = "component_name", nullable = false, length = 120, updatable = false)
    private String componentName;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "is_taxable", nullable = false, updatable = false)
    private boolean taxable;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected EmployeePayRunLine() {}

    public EmployeePayRunLine(
            UUID tenantId, UUID employeePayrunId, UUID payrunId, PayLine line, int sortOrder, String actor) {
        Objects.requireNonNull(line, "line must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeePayrunId = Objects.requireNonNull(employeePayrunId, "employeePayrunId must not be null");
        this.payrunId = Objects.requireNonNull(payrunId, "payrunId must not be null");
        this.lineKind = line.kind();
        this.source = line.source();
        this.componentId = line.componentId();
        this.componentCode = truncate(line.componentCode(), 64);
        this.componentName = truncate(line.componentName(), 120);
        this.amount = line.amount().raw();
        this.taxable = line.taxable();
        this.sortOrder = sortOrder;
        this.createdBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    private static String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.createdAt = now;
        this.updatedAt = now;
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

    public UUID getEmployeePayrunId() {
        return employeePayrunId;
    }

    public UUID getPayrunId() {
        return payrunId;
    }

    public LineKind getLineKind() {
        return lineKind;
    }

    public LineSource getSource() {
        return source;
    }

    public UUID getComponentId() {
        return componentId;
    }

    public String getComponentCode() {
        return componentCode;
    }

    public String getComponentName() {
        return componentName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isTaxable() {
        return taxable;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
