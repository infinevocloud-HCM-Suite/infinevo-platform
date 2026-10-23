package com.infinevo.core.wfh;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing tenant-level WFH rules and policy configuration (W-74).
 */
@Entity
@Audited
@Table(
        name = "wfh_policy",
        schema = "core",
        indexes = {@Index(name = "idx_wfh_policy_tenant", columnList = "tenant_id, is_active")})
public class WfhPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "annual_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal annualDays = new BigDecimal("12.00");

    @Column(name = "monthly_limit_days", precision = 10, scale = 2)
    private BigDecimal monthlyLimitDays = new BigDecimal("2.00");

    @Column(name = "allow_half_day", nullable = false)
    private boolean allowHalfDay = true;

    @Column(name = "advance_notice_days", nullable = false)
    private int advanceNoticeDays = 1;

    @Column(name = "max_consecutive_days")
    private Integer maxConsecutiveDays = 3;

    @Column(name = "requires_manager_approval", nullable = false)
    private boolean requiresManagerApproval = true;

    @Column(name = "requires_hr_approval", nullable = false)
    private boolean requiresHrApproval = false;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAnnualDays() {
        return annualDays;
    }

    public void setAnnualDays(BigDecimal annualDays) {
        this.annualDays = annualDays;
    }

    public BigDecimal getMonthlyLimitDays() {
        return monthlyLimitDays;
    }

    public void setMonthlyLimitDays(BigDecimal monthlyLimitDays) {
        this.monthlyLimitDays = monthlyLimitDays;
    }

    public boolean isAllowHalfDay() {
        return allowHalfDay;
    }

    public void setAllowHalfDay(boolean allowHalfDay) {
        this.allowHalfDay = allowHalfDay;
    }

    public int getAdvanceNoticeDays() {
        return advanceNoticeDays;
    }

    public void setAdvanceNoticeDays(int advanceNoticeDays) {
        this.advanceNoticeDays = advanceNoticeDays;
    }

    public Integer getMaxConsecutiveDays() {
        return maxConsecutiveDays;
    }

    public void setMaxConsecutiveDays(Integer maxConsecutiveDays) {
        this.maxConsecutiveDays = maxConsecutiveDays;
    }

    public boolean isRequiresManagerApproval() {
        return requiresManagerApproval;
    }

    public void setRequiresManagerApproval(boolean requiresManagerApproval) {
        this.requiresManagerApproval = requiresManagerApproval;
    }

    public boolean isRequiresHrApproval() {
        return requiresHrApproval;
    }

    public void setRequiresHrApproval(boolean requiresHrApproval) {
        this.requiresHrApproval = requiresHrApproval;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WfhPolicy wfhPolicy = (WfhPolicy) o;
        return Objects.equals(id, wfhPolicy.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
