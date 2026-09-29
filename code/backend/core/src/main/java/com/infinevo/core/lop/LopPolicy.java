package com.infinevo.core.lop;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Loss-of-pay and working-day policy entity (W-18.1) — {@code core.lop_policy}.
 *
 * <p>Versioned per tenant by {@code effective_from} so that past pay runs remain explainable
 * and reproducible even after policies change.
 */
@Entity
@Table(
        name = "lop_policy",
        schema = "core",
        indexes = {@Index(name = "idx_lop_policy_tenant_effective", columnList = "tenant_id, effective_from DESC")})
public class LopPolicy {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "working_day_basis", nullable = false, length = 24)
    private WorkingDayBasis workingDayBasis;

    @Column(name = "configured_days_per_month", precision = 10, scale = 2)
    private BigDecimal configuredDaysPerMonth;

    @Column(name = "weekends_payable", nullable = false)
    private boolean weekendsPayable = true;

    @Column(name = "holidays_payable", nullable = false)
    private boolean holidaysPayable = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "lop_rounding", nullable = false, length = 16)
    private LopRounding lopRounding = LopRounding.HALF_UP_2;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected LopPolicy() {}

    public LopPolicy(
            UUID tenantId,
            WorkingDayBasis workingDayBasis,
            BigDecimal configuredDaysPerMonth,
            boolean weekendsPayable,
            boolean holidaysPayable,
            LopRounding lopRounding,
            LocalDate effectiveFrom) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.workingDayBasis = Objects.requireNonNull(workingDayBasis, "workingDayBasis must not be null");
        this.configuredDaysPerMonth = configuredDaysPerMonth;
        this.weekendsPayable = weekendsPayable;
        this.holidaysPayable = holidaysPayable;
        this.lopRounding = lopRounding != null ? lopRounding : LopRounding.HALF_UP_2;
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (lopRounding == null) {
            lopRounding = LopRounding.HALF_UP_2;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        if (lopRounding == null) {
            lopRounding = LopRounding.HALF_UP_2;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public WorkingDayBasis getWorkingDayBasis() {
        return workingDayBasis;
    }

    public void setWorkingDayBasis(WorkingDayBasis workingDayBasis) {
        this.workingDayBasis = Objects.requireNonNull(workingDayBasis, "workingDayBasis must not be null");
    }

    public BigDecimal getConfiguredDaysPerMonth() {
        return configuredDaysPerMonth;
    }

    public void setConfiguredDaysPerMonth(BigDecimal configuredDaysPerMonth) {
        this.configuredDaysPerMonth = configuredDaysPerMonth;
    }

    public boolean isWeekendsPayable() {
        return weekendsPayable;
    }

    public void setWeekendsPayable(boolean weekendsPayable) {
        this.weekendsPayable = weekendsPayable;
    }

    public boolean isHolidaysPayable() {
        return holidaysPayable;
    }

    public void setHolidaysPayable(boolean holidaysPayable) {
        this.holidaysPayable = holidaysPayable;
    }

    public LopRounding getLopRounding() {
        return lopRounding;
    }

    public void setLopRounding(LopRounding lopRounding) {
        this.lopRounding = lopRounding != null ? lopRounding : LopRounding.HALF_UP_2;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
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
