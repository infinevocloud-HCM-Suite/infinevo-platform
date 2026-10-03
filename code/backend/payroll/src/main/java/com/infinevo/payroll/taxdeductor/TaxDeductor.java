package com.infinevo.payroll.taxdeductor;

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
 * Tax deductor entity holding employer TAN, PAN, TDS circle and authorized signatory (W-36.3).
 */
@Entity
@Audited
@Table(
        name = "tax_deductor",
        schema = "payroll",
        indexes = {
            @Index(name = "uk_tax_deductor_tenant", columnList = "tenant_id", unique = true),
            @Index(name = "idx_tax_deductor_tenant_signatory", columnList = "tenant_id, signatory_employee_id")
        })
public class TaxDeductor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "tan", nullable = false, length = 10)
    private String tan;

    @Column(name = "pan", nullable = false, length = 10)
    private String pan;

    @Column(name = "tds_circle", length = 13)
    private String tdsCircle;

    @Column(name = "signatory_employee_id")
    private UUID signatoryEmployeeId;

    @Column(name = "signatory_name", nullable = false, length = 120)
    private String signatoryName;

    @Column(name = "signatory_parent_name", length = 120)
    private String signatoryParentName;

    @Column(name = "signatory_designation", nullable = false, length = 120)
    private String signatoryDesignation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public TaxDeductor() {}

    public TaxDeductor(UUID tenantId, String createdBy) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.createdBy = createdBy != null && !createdBy.isBlank() ? createdBy : "system";
        this.updatedBy = this.createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTan() {
        return tan;
    }

    public void setTan(String tan) {
        this.tan = tan;
    }

    public String getPan() {
        return pan;
    }

    public void setPan(String pan) {
        this.pan = pan;
    }

    public String getTdsCircle() {
        return tdsCircle;
    }

    public void setTdsCircle(String tdsCircle) {
        this.tdsCircle = tdsCircle;
    }

    public UUID getSignatoryEmployeeId() {
        return signatoryEmployeeId;
    }

    public void setSignatoryEmployeeId(UUID signatoryEmployeeId) {
        this.signatoryEmployeeId = signatoryEmployeeId;
    }

    public String getSignatoryName() {
        return signatoryName;
    }

    public void setSignatoryName(String signatoryName) {
        this.signatoryName = signatoryName;
    }

    public String getSignatoryParentName() {
        return signatoryParentName;
    }

    public void setSignatoryParentName(String signatoryParentName) {
        this.signatoryParentName = signatoryParentName;
    }

    public String getSignatoryDesignation() {
        return signatoryDesignation;
    }

    public void setSignatoryDesignation(String signatoryDesignation) {
        this.signatoryDesignation = signatoryDesignation;
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
