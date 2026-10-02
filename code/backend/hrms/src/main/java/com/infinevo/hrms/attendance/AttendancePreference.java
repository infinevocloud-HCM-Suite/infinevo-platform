package com.infinevo.hrms.attendance;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-scoped attendance preference configuration (W-40.1).
 *
 * <p>Persisted in {@code hrms.attendance_preference} with row-level security isolation.
 * Changes are audited to {@code core.audit_log} through {@link Audited}.
 */
@Entity
@Table(name = "attendance_preference", schema = "hrms")
@Audited
public class AttendancePreference {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "hours_calculation", nullable = false, length = 24)
    private HoursCalculation hoursCalculation = HoursCalculation.EVERY_SESSION;

    @Column(name = "full_day_minimum_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal fullDayMinimumHours = BigDecimal.valueOf(9.00).setScale(2);

    @Column(name = "half_day_minimum_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal halfDayMinimumHours = BigDecimal.valueOf(4.50).setScale(2);

    @Column(name = "regularization_window_days")
    private Integer regularizationWindowDays;

    @Column(name = "max_regularizations_per_month")
    private Integer maxRegularizationsPerMonth;

    @Column(name = "allow_regularization_without_session", nullable = false)
    private boolean allowRegularizationWithoutSession = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    public AttendancePreference() {}

    public AttendancePreference(
            UUID tenantId,
            HoursCalculation hoursCalculation,
            BigDecimal fullDayMinimumHours,
            BigDecimal halfDayMinimumHours,
            Integer regularizationWindowDays,
            Integer maxRegularizationsPerMonth,
            boolean allowRegularizationWithoutSession) {
        this.tenantId = tenantId;
        this.hoursCalculation = hoursCalculation;
        this.fullDayMinimumHours = fullDayMinimumHours;
        this.halfDayMinimumHours = halfDayMinimumHours;
        this.regularizationWindowDays = regularizationWindowDays;
        this.maxRegularizationsPerMonth = maxRegularizationsPerMonth;
        this.allowRegularizationWithoutSession = allowRegularizationWithoutSession;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = ACTOR_SYSTEM;
        }
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = ACTOR_SYSTEM;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = ACTOR_SYSTEM;
        }
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

    public HoursCalculation getHoursCalculation() {
        return hoursCalculation;
    }

    public void setHoursCalculation(HoursCalculation hoursCalculation) {
        this.hoursCalculation = hoursCalculation;
    }

    public BigDecimal getFullDayMinimumHours() {
        return fullDayMinimumHours;
    }

    public void setFullDayMinimumHours(BigDecimal fullDayMinimumHours) {
        this.fullDayMinimumHours = fullDayMinimumHours;
    }

    public BigDecimal getHalfDayMinimumHours() {
        return halfDayMinimumHours;
    }

    public void setHalfDayMinimumHours(BigDecimal halfDayMinimumHours) {
        this.halfDayMinimumHours = halfDayMinimumHours;
    }

    public Integer getRegularizationWindowDays() {
        return regularizationWindowDays;
    }

    public void setRegularizationWindowDays(Integer regularizationWindowDays) {
        this.regularizationWindowDays = regularizationWindowDays;
    }

    public Integer getMaxRegularizationsPerMonth() {
        return maxRegularizationsPerMonth;
    }

    public void setMaxRegularizationsPerMonth(Integer maxRegularizationsPerMonth) {
        this.maxRegularizationsPerMonth = maxRegularizationsPerMonth;
    }

    public boolean isAllowRegularizationWithoutSession() {
        return allowRegularizationWithoutSession;
    }

    public void setAllowRegularizationWithoutSession(boolean allowRegularizationWithoutSession) {
        this.allowRegularizationWithoutSession = allowRegularizationWithoutSession;
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
