package com.infinevo.core.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Leave policy eligibility criteria by dimension and value (W-16.1, spec section 4 & 6).
 */
@Entity
@Table(
        name = "leave_policy_eligibility",
        schema = "core",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_leave_policy_eligibility",
                    columnNames = {"tenant_id", "policy_id", "dimension", "value_id"})
        },
        indexes = {@Index(name = "idx_leave_policy_eligibility_lookup", columnList = "tenant_id, policy_id")})
public class LeavePolicyEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "dimension", nullable = false, length = 32)
    private String dimension;

    @Column(name = "value_id", nullable = false)
    private UUID valueId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public LeavePolicyEligibility() {}

    public LeavePolicyEligibility(UUID tenantId, UUID policyId, String dimension, UUID valueId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.policyId = Objects.requireNonNull(policyId, "policyId must not be null");
        this.dimension = Objects.requireNonNull(dimension, "dimension must not be null");
        this.valueId = Objects.requireNonNull(valueId, "valueId must not be null");
    }

    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (createdBy == null) {
            createdBy = "system";
        }
        if (updatedBy == null) {
            updatedBy = "system";
        }
    }

    @PreUpdate
    void onPreUpdate() {
        updatedAt = Instant.now();
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

    public UUID getPolicyId() {
        return policyId;
    }

    public void setPolicyId(UUID policyId) {
        this.policyId = policyId;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public UUID getValueId() {
        return valueId;
    }

    public void setValueId(UUID valueId) {
        this.valueId = valueId;
    }

    public Instant getCreatedAt() {
        return createdAt;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
