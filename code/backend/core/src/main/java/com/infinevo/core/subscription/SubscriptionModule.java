package com.infinevo.core.subscription;

import com.infinevo.shared.entitlement.PlatformModule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Module entitlement line for a tenant subscription (W-12.1) — {@code core.subscription_module}.
 *
 * <p>Never hard-deleted upon downgrade: a revoked module records {@link #revokedOn}
 * to explain historical artifacts and records created while the module was active (decision 1).
 */
@Entity
@Table(name = "subscription_module", schema = "core")
public class SubscriptionModule {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 16)
    private PlatformModule module;

    @Column(name = "granted_on", nullable = false)
    private LocalDate grantedOn;

    @Column(name = "revoked_on")
    private LocalDate revokedOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected SubscriptionModule() {}

    public SubscriptionModule(UUID tenantId, Subscription subscription, PlatformModule module, LocalDate grantedOn) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.subscription = Objects.requireNonNull(subscription, "subscription must not be null");
        this.module = Objects.requireNonNull(module, "module must not be null");
        this.grantedOn = Objects.requireNonNull(grantedOn, "grantedOn must not be null");
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (grantedOn == null) {
            grantedOn = LocalDate.now();
        }
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

    public Subscription getSubscription() {
        return subscription;
    }

    public PlatformModule getModule() {
        return module;
    }

    public LocalDate getGrantedOn() {
        return grantedOn;
    }

    public void setGrantedOn(LocalDate grantedOn) {
        this.grantedOn = grantedOn;
    }

    public LocalDate getRevokedOn() {
        return revokedOn;
    }

    public void setRevokedOn(LocalDate revokedOn) {
        this.revokedOn = revokedOn;
    }

    public boolean isActive() {
        return revokedOn == null;
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
