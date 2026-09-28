package com.infinevo.payroll.statutory.pt;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tenant-level professional tax override header (W-31.2, V065).
 */
@Entity
@Table(name = "org_pt_override", schema = "payroll")
@Audited
public class OrgPtOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "state_code", length = 10, nullable = false)
    private String stateCode;

    @Column(name = "registration_number", length = 32)
    private String registrationNumber;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100, nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100, nullable = false)
    private String updatedBy;

    protected OrgPtOverride() {}

    public OrgPtOverride(
            UUID tenantId, String stateCode, String registrationNumber, LocalDate effectiveFrom, String actor) {
        this.tenantId = tenantId;
        this.stateCode = stateCode;
        this.registrationNumber = registrationNumber;
        this.effectiveFrom = effectiveFrom;
        this.createdAt = Instant.now();
        this.createdBy = actor != null ? actor : "system";
        this.updatedAt = Instant.now();
        this.updatedBy = this.createdBy;
    }

    public void update(String registrationNumber, LocalDate effectiveFrom, String actor) {
        this.registrationNumber = registrationNumber;
        this.effectiveFrom = effectiveFrom;
        this.updatedAt = Instant.now();
        this.updatedBy = actor != null ? actor : "system";
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
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
