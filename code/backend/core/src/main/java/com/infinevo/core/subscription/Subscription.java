package com.infinevo.core.subscription;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Root subscription entity for a tenant (W-12.1) — {@code core.subscription}.
 *
 * <p>Contains zero price or currency columns (D-12, DEBT-018): billing stays behind
 * the payment seam ({@link #externalRef}) and does not leak into the core schema.
 *
 * <p>{@link Audited} (W-84.1): a change of plan, support plan or employee limit, and of status, is recorded in
 * {@code core.audit_log} with its old and new values and the actor.
 */
@Entity
@Table(name = "subscription", schema = "core")
@Audited
public class Subscription {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SubscriptionStatus status;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "current_period_end")
    private LocalDate currentPeriodEnd;

    @Column(name = "external_ref", length = 128)
    private String externalRef;

    // W-84.1 (V188): a billing label, the support tier and the employee limit. All nullable; a null limit is
    // unlimited.
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", length = 16)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "support_plan", length = 16)
    private SupportPlan supportPlan;

    @Column(name = "employee_limit")
    private Integer employeeLimit;

    @OneToMany(mappedBy = "subscription", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<SubscriptionModule> modules = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected Subscription() {}

    public Subscription(UUID tenantId, SubscriptionStatus status, LocalDate startedOn) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.startedOn = Objects.requireNonNull(startedOn, "startedOn must not be null");
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (startedOn == null) {
            startedOn = LocalDate.now();
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

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }

    public void setStartedOn(LocalDate startedOn) {
        this.startedOn = startedOn;
    }

    public LocalDate getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(LocalDate currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public void setExternalRef(String externalRef) {
        this.externalRef = externalRef;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public void setPlan(SubscriptionPlan plan) {
        this.plan = plan;
    }

    public SupportPlan getSupportPlan() {
        return supportPlan;
    }

    public void setSupportPlan(SupportPlan supportPlan) {
        this.supportPlan = supportPlan;
    }

    public Integer getEmployeeLimit() {
        return employeeLimit;
    }

    public void setEmployeeLimit(Integer employeeLimit) {
        this.employeeLimit = employeeLimit;
    }

    public List<SubscriptionModule> getModules() {
        return Collections.unmodifiableList(modules);
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
