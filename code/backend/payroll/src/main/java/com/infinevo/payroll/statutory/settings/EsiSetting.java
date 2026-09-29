package com.infinevo.payroll.statutory.settings;

import com.infinevo.shared.audit.Audited;
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
import java.util.UUID;

/**
 * State insurance (ESI) settings entity in schema payroll (W-31.1).
 */
@Entity
@Audited
@Table(
        name = "esi_setting",
        schema = "payroll",
        indexes = {@Index(name = "uk_esi_setting_tenant", columnList = "tenant_id", unique = true)})
public class EsiSetting {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "registration_number", length = 32)
    private String registrationNumber;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "deduction_cycle", nullable = false, length = 16)
    private DeductionCycle deductionCycle = DeductionCycle.MONTHLY;

    @Column(name = "employee_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal employeeRate = new BigDecimal("0.7500");

    @Column(name = "employer_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal employerRate = new BigDecimal("3.2500");

    @Column(name = "wage_ceiling", nullable = false, precision = 19, scale = 4)
    private BigDecimal wageCeiling = new BigDecimal("21000.0000");

    @Column(name = "include_employer_in_ctc", nullable = false)
    private boolean includeEmployerInCtc = false;

    @Column(name = "include_in_structure", nullable = false)
    private boolean includeInStructure = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    public EsiSetting() {}

    public EsiSetting(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public EsiSetting(UUID tenantId, String createdBy) {
        this.tenantId = tenantId;
        this.createdBy = (createdBy != null && !createdBy.isBlank()) ? createdBy : ACTOR_SYSTEM;
        this.updatedBy = this.createdBy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
    }

    public DeductionCycle getDeductionCycle() {
        return deductionCycle;
    }

    public void setDeductionCycle(DeductionCycle deductionCycle) {
        this.deductionCycle = deductionCycle;
    }

    public BigDecimal getEmployeeRate() {
        return employeeRate;
    }

    public void setEmployeeRate(BigDecimal employeeRate) {
        this.employeeRate = employeeRate;
    }

    public BigDecimal getEmployerRate() {
        return employerRate;
    }

    public void setEmployerRate(BigDecimal employerRate) {
        this.employerRate = employerRate;
    }

    public BigDecimal getWageCeiling() {
        return wageCeiling;
    }

    public void setWageCeiling(BigDecimal wageCeiling) {
        this.wageCeiling = wageCeiling;
    }

    public boolean isIncludeEmployerInCtc() {
        return includeEmployerInCtc;
    }

    public void setIncludeEmployerInCtc(boolean includeEmployerInCtc) {
        this.includeEmployerInCtc = includeEmployerInCtc;
    }

    public boolean isIncludeInStructure() {
        return includeInStructure;
    }

    public void setIncludeInStructure(boolean includeInStructure) {
        this.includeInStructure = includeInStructure;
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
