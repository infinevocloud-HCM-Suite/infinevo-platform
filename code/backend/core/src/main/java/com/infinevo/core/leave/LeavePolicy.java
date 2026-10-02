package com.infinevo.core.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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
import java.util.UUID;

/**
 * Tenant-scoped leave policy matrix with effective-date versioning (W-16.1, spec section 4 & 6).
 */
@Entity
@Table(
        name = "leave_policy",
        schema = "core",
        indexes = {
            @Index(name = "idx_leave_policy_lookup", columnList = "tenant_id, leave_type_id, effective_from DESC")
        })
public class LeavePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "annual_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal annualDays;

    @Column(name = "accrual_enabled")
    private Boolean accrualEnabled;

    @Convert(converter = AccrualFrequency.JpaConverter.class)
    @Column(name = "accrual_frequency", length = 16)
    private AccrualFrequency accrualFrequency;

    @Column(name = "accrual_units", precision = 10, scale = 2)
    private BigDecimal accrualUnits;

    @Column(name = "reset_enabled")
    private Boolean resetEnabled;

    @Convert(converter = ResetFrequency.JpaConverter.class)
    @Column(name = "reset_frequency", length = 16)
    private ResetFrequency resetFrequency;

    @Column(name = "carry_forward_enabled")
    private Boolean carryForwardEnabled;

    @Column(name = "carry_forward_cap", precision = 10, scale = 2)
    private BigDecimal carryForwardCap;

    @Column(name = "carry_forward_expires_after_months")
    private Integer carryForwardExpiresAfterMonths;

    @Column(name = "requires_document", nullable = false)
    private boolean requiresDocument;

    @Column(name = "past_booking_limit_days")
    private Integer pastBookingLimitDays;

    @Column(name = "future_booking_limit_days")
    private Integer futureBookingLimitDays;

    @Column(name = "include_weekend")
    private Boolean includeWeekend;

    @Column(name = "include_holiday")
    private Boolean includeHoliday;

    @Convert(converter = ExceedBalanceMode.JpaConverter.class)
    @Column(name = "exceed_balance_mode", nullable = false, length = 16)
    private ExceedBalanceMode exceedBalanceMode;

    @Column(name = "exceed_balance_limit_days", precision = 10, scale = 2)
    private BigDecimal exceedBalanceLimitDays;

    @Column(name = "pro_rate_enabled")
    private Boolean proRateEnabled;

    @Column(name = "max_days_per_application", precision = 10, scale = 2)
    private BigDecimal maxDaysPerApplication;

    @Column(name = "gender", length = 32)
    private String gender;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public LeavePolicy() {}

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

    public UUID getLeaveTypeId() {
        return leaveTypeId;
    }

    public void setLeaveTypeId(UUID leaveTypeId) {
        this.leaveTypeId = leaveTypeId;
    }

    public BigDecimal getAnnualDays() {
        return annualDays;
    }

    public void setAnnualDays(BigDecimal annualDays) {
        this.annualDays = annualDays;
    }

    public Boolean getAccrualEnabled() {
        return accrualEnabled;
    }

    public void setAccrualEnabled(Boolean accrualEnabled) {
        this.accrualEnabled = accrualEnabled;
    }

    public AccrualFrequency getAccrualFrequency() {
        return accrualFrequency;
    }

    public void setAccrualFrequency(AccrualFrequency accrualFrequency) {
        this.accrualFrequency = accrualFrequency;
    }

    public BigDecimal getAccrualUnits() {
        return accrualUnits;
    }

    public void setAccrualUnits(BigDecimal accrualUnits) {
        this.accrualUnits = accrualUnits;
    }

    public Boolean getResetEnabled() {
        return resetEnabled;
    }

    public void setResetEnabled(Boolean resetEnabled) {
        this.resetEnabled = resetEnabled;
    }

    public ResetFrequency getResetFrequency() {
        return resetFrequency;
    }

    public void setResetFrequency(ResetFrequency resetFrequency) {
        this.resetFrequency = resetFrequency;
    }

    public Boolean getCarryForwardEnabled() {
        return carryForwardEnabled;
    }

    public void setCarryForwardEnabled(Boolean carryForwardEnabled) {
        this.carryForwardEnabled = carryForwardEnabled;
    }

    public BigDecimal getCarryForwardCap() {
        return carryForwardCap;
    }

    public void setCarryForwardCap(BigDecimal carryForwardCap) {
        this.carryForwardCap = carryForwardCap;
    }

    public Integer getCarryForwardExpiresAfterMonths() {
        return carryForwardExpiresAfterMonths;
    }

    public void setCarryForwardExpiresAfterMonths(Integer carryForwardExpiresAfterMonths) {
        this.carryForwardExpiresAfterMonths = carryForwardExpiresAfterMonths;
    }

    public boolean isRequiresDocument() {
        return requiresDocument;
    }

    public void setRequiresDocument(boolean requiresDocument) {
        this.requiresDocument = requiresDocument;
    }

    public Integer getPastBookingLimitDays() {
        return pastBookingLimitDays;
    }

    public void setPastBookingLimitDays(Integer pastBookingLimitDays) {
        this.pastBookingLimitDays = pastBookingLimitDays;
    }

    public Integer getFutureBookingLimitDays() {
        return futureBookingLimitDays;
    }

    public void setFutureBookingLimitDays(Integer futureBookingLimitDays) {
        this.futureBookingLimitDays = futureBookingLimitDays;
    }

    public Boolean getIncludeWeekend() {
        return includeWeekend;
    }

    public void setIncludeWeekend(Boolean includeWeekend) {
        this.includeWeekend = includeWeekend;
    }

    public Boolean getIncludeHoliday() {
        return includeHoliday;
    }

    public void setIncludeHoliday(Boolean includeHoliday) {
        this.includeHoliday = includeHoliday;
    }

    public ExceedBalanceMode getExceedBalanceMode() {
        return exceedBalanceMode;
    }

    public void setExceedBalanceMode(ExceedBalanceMode exceedBalanceMode) {
        this.exceedBalanceMode = exceedBalanceMode;
    }

    public BigDecimal getExceedBalanceLimitDays() {
        return exceedBalanceLimitDays;
    }

    public void setExceedBalanceLimitDays(BigDecimal exceedBalanceLimitDays) {
        this.exceedBalanceLimitDays = exceedBalanceLimitDays;
    }

    public Boolean getProRateEnabled() {
        return proRateEnabled;
    }

    public void setProRateEnabled(Boolean proRateEnabled) {
        this.proRateEnabled = proRateEnabled;
    }

    public BigDecimal getMaxDaysPerApplication() {
        return maxDaysPerApplication;
    }

    public void setMaxDaysPerApplication(BigDecimal maxDaysPerApplication) {
        this.maxDaysPerApplication = maxDaysPerApplication;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
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
