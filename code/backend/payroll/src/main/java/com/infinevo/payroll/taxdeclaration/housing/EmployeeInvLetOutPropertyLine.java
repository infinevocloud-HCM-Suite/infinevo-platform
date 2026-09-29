package com.infinevo.payroll.taxdeclaration.housing;

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
 * Breakdown lines (annual rent, municipal tax, loan interest) per let-out property (W-32.2).
 */
@Entity
@Table(schema = "payroll", name = "employee_inv_let_out_property_line")
public class EmployeeInvLetOutPropertyLine {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 16)
    private LetOutPropertyLineType lineType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "lender_name", length = 150)
    private String lenderName;

    @Column(name = "lender_pan", length = 10)
    private String lenderPan;

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

    public EmployeeInvLetOutPropertyLine() {}

    public EmployeeInvLetOutPropertyLine(
            UUID tenantId,
            UUID propertyId,
            LetOutPropertyLineType lineType,
            BigDecimal amount,
            String lenderName,
            String lenderPan) {
        this(tenantId, null, propertyId, lineType, amount, lenderName, lenderPan);
    }

    public EmployeeInvLetOutPropertyLine(
            UUID tenantId,
            UUID declarationId,
            UUID propertyId,
            LetOutPropertyLineType lineType,
            BigDecimal amount,
            String lenderName,
            String lenderPan) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.declarationId = declarationId;
        this.propertyId = Objects.requireNonNull(propertyId, "propertyId must not be null");
        this.lineType = Objects.requireNonNull(lineType, "lineType must not be null");
        this.amount = amount != null ? amount : BigDecimal.ZERO;
        this.lenderName = lenderName;
        this.lenderPan = lenderPan;
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

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public LetOutPropertyLineType getLineType() {
        return lineType;
    }

    public void setLineType(LetOutPropertyLineType lineType) {
        this.lineType = lineType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getLenderName() {
        return lenderName;
    }

    public void setLenderName(String lenderName) {
        this.lenderName = lenderName;
    }

    public String getLenderPan() {
        return lenderPan;
    }

    public void setLenderPan(String lenderPan) {
        this.lenderPan = lenderPan;
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
