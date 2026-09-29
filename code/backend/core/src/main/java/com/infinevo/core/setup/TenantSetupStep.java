package com.infinevo.core.setup;

import com.infinevo.shared.entitlement.PlatformModule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Onboarding setup step row for a tenant (W-24.1) — {@code core.tenant_setup_step}.
 *
 * <p>One row per applicable step per tenant, assembled from the tenant's subscribed modules.
 * {@code completed_at} caches the answer of the step's {@link SetupStepChecker} and is refreshed
 * on read; if the underlying data is deleted, {@code completed_at} reverts to null.
 */
@Entity
@Table(
        name = "tenant_setup_step",
        schema = "core",
        indexes = {
            @Index(name = "idx_tenant_setup_step_tenant_step", columnList = "tenant_id, step_code", unique = true),
            @Index(name = "idx_tenant_setup_step_tenant_order", columnList = "tenant_id, display_order")
        })
public class TenantSetupStep {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "step_code", nullable = false, length = 64)
    private String stepCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", length = 16)
    private PlatformModule module;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_skipped", nullable = false)
    private boolean skipped = false;

    @Column(name = "skip_reason", length = 500)
    private String skipReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected TenantSetupStep() {}

    public TenantSetupStep(
            UUID tenantId, String stepCode, PlatformModule module, int displayOrder, Instant firstSeenAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.stepCode = Objects.requireNonNull(stepCode, "stepCode must not be null");
        this.module = module;
        this.displayOrder = displayOrder;
        this.firstSeenAt = firstSeenAt != null ? firstSeenAt : Instant.now();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (firstSeenAt == null) {
            firstSeenAt = now;
        }
        if (createdBy == null) {
            createdBy = ACTOR_SYSTEM;
        }
        if (updatedBy == null) {
            updatedBy = ACTOR_SYSTEM;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        if (updatedBy == null) {
            updatedBy = ACTOR_SYSTEM;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getStepCode() {
        return stepCode;
    }

    public PlatformModule getModule() {
        return module;
    }

    public void setModule(PlatformModule module) {
        this.module = module;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
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
