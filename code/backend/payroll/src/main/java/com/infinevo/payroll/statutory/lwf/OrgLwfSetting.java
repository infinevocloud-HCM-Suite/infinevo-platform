package com.infinevo.payroll.statutory.lwf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Tenant LWF registration and active/inactive toggle per state (W-92).
 * Stores only tenant configuration; standard contribution rules live in reference.lwf_rule.
 */
@Entity
@Table(name = "org_lwf_setting", schema = "payroll")
public class OrgLwfSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "state_code", length = 10, nullable = false)
    private String stateCode;

    @Column(name = "registration_number", length = 32)
    private String registrationNumber;

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", length = 64, nullable = false)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", length = 64, nullable = false)
    private String updatedBy = "system";

    protected OrgLwfSetting() {}

    public OrgLwfSetting(UUID tenantId, String stateCode, String registrationNumber, boolean isEnabled, String actor) {
        this.tenantId = tenantId;
        this.stateCode = stateCode;
        this.registrationNumber = registrationNumber;
        this.isEnabled = isEnabled;
        this.createdAt = Instant.now();
        this.createdBy = actor != null ? actor : "system";
        this.updatedAt = this.createdAt;
        this.updatedBy = this.createdBy;
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

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
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

    public void setUpdated(String actor) {
        this.updatedAt = Instant.now();
        this.updatedBy = actor != null ? actor : "system";
    }
}
