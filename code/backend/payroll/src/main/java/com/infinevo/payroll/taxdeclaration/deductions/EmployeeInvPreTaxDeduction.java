package com.infinevo.payroll.taxdeclaration.deductions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Declared pre-tax salary deduction (VPF, employee NPS, PF, Professional Tax) (W-32.3).
 */
@Entity
@Table(schema = "payroll", name = "employee_inv_pre_tax_deduction")
public class EmployeeInvPreTaxDeduction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24)
    private PreTaxDeductionKind kind;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public EmployeeInvPreTaxDeduction() {}

    public EmployeeInvPreTaxDeduction(UUID tenantId, UUID declarationId, PreTaxDeductionKind kind, BigDecimal amount) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.declarationId = Objects.requireNonNull(declarationId, "declarationId must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.amount = amount != null ? amount : BigDecimal.ZERO;
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

    public UUID getDeclarationId() {
        return declarationId;
    }

    public void setDeclarationId(UUID declarationId) {
        this.declarationId = declarationId;
    }

    public PreTaxDeductionKind getKind() {
        return kind;
    }

    public void setKind(PreTaxDeductionKind kind) {
        this.kind = kind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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
