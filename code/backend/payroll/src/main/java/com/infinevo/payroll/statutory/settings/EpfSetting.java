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
 * Provident fund settings entity in schema payroll (W-31.1).
 */
@Entity
@Audited
@Table(
        name = "epf_setting",
        schema = "payroll",
        indexes = {@Index(name = "uk_epf_setting_tenant", columnList = "tenant_id", unique = true)})
public class EpfSetting {

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
    private BigDecimal employeeRate = new BigDecimal("12.0000");

    @Column(name = "employer_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal employerRate = new BigDecimal("12.0000");

    @Column(name = "eps_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal epsRate = new BigDecimal("8.3300");

    @Column(name = "edli_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal edliRate = new BigDecimal("0.5000");

    @Column(name = "admin_charge_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal adminChargeRate = new BigDecimal("0.5000");

    @Column(name = "wage_ceiling", nullable = false, precision = 19, scale = 4)
    private BigDecimal wageCeiling = new BigDecimal("15000.0000");

    @Column(name = "restrict_employee_to_ceiling", nullable = false)
    private boolean restrictEmployeeToCeiling = false;

    @Column(name = "restrict_employer_to_ceiling", nullable = false)
    private boolean restrictEmployerToCeiling = false;

    @Column(name = "prorate_restricted_wage", nullable = false)
    private boolean prorateRestrictedWage = false;

    @Column(name = "consider_earned_wage", nullable = false)
    private boolean considerEarnedWage = true;

    @Column(name = "eps_senior_age", nullable = false)
    private short epsSeniorAge = 58;

    @Column(name = "include_employer_in_ctc", nullable = false)
    private boolean includeEmployerInCtc = false;

    @Column(name = "include_edli_admin_in_ctc", nullable = false)
    private boolean includeEdliAdminInCtc = false;

    @Column(name = "include_employer_in_structure", nullable = false)
    private boolean includeEmployerInStructure = false;

    @Column(name = "include_edli_admin_in_structure", nullable = false)
    private boolean includeEdliAdminInStructure = false;

    @Column(name = "abry_scheme", nullable = false)
    private boolean abryScheme = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    public EpfSetting() {}

    public EpfSetting(UUID tenantId) {
        this.tenantId = tenantId;
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

    public BigDecimal getEpsRate() {
        return epsRate;
    }

    public void setEpsRate(BigDecimal epsRate) {
        this.epsRate = epsRate;
    }

    public BigDecimal getEdliRate() {
        return edliRate;
    }

    public void setEdliRate(BigDecimal edliRate) {
        this.edliRate = edliRate;
    }

    public BigDecimal getAdminChargeRate() {
        return adminChargeRate;
    }

    public void setAdminChargeRate(BigDecimal adminChargeRate) {
        this.adminChargeRate = adminChargeRate;
    }

    public BigDecimal getWageCeiling() {
        return wageCeiling;
    }

    public void setWageCeiling(BigDecimal wageCeiling) {
        this.wageCeiling = wageCeiling;
    }

    public boolean isRestrictEmployeeToCeiling() {
        return restrictEmployeeToCeiling;
    }

    public void setRestrictEmployeeToCeiling(boolean restrictEmployeeToCeiling) {
        this.restrictEmployeeToCeiling = restrictEmployeeToCeiling;
    }

    public boolean isRestrictEmployerToCeiling() {
        return restrictEmployerToCeiling;
    }

    public void setRestrictEmployerToCeiling(boolean restrictEmployerToCeiling) {
        this.restrictEmployerToCeiling = restrictEmployerToCeiling;
    }

    public boolean isProrateRestrictedWage() {
        return prorateRestrictedWage;
    }

    public void setProrateRestrictedWage(boolean prorateRestrictedWage) {
        this.prorateRestrictedWage = prorateRestrictedWage;
    }

    public boolean isConsiderEarnedWage() {
        return considerEarnedWage;
    }

    public void setConsiderEarnedWage(boolean considerEarnedWage) {
        this.considerEarnedWage = considerEarnedWage;
    }

    public short getEpsSeniorAge() {
        return epsSeniorAge;
    }

    public void setEpsSeniorAge(short epsSeniorAge) {
        this.epsSeniorAge = epsSeniorAge;
    }

    public boolean isIncludeEmployerInCtc() {
        return includeEmployerInCtc;
    }

    public void setIncludeEmployerInCtc(boolean includeEmployerInCtc) {
        this.includeEmployerInCtc = includeEmployerInCtc;
    }

    public boolean isIncludeEdliAdminInCtc() {
        return includeEdliAdminInCtc;
    }

    public void setIncludeEdliAdminInCtc(boolean includeEdliAdminInCtc) {
        this.includeEdliAdminInCtc = includeEdliAdminInCtc;
    }

    public boolean isIncludeEmployerInStructure() {
        return includeEmployerInStructure;
    }

    public void setIncludeEmployerInStructure(boolean includeEmployerInStructure) {
        this.includeEmployerInStructure = includeEmployerInStructure;
    }

    public boolean isIncludeEdliAdminInStructure() {
        return includeEdliAdminInStructure;
    }

    public void setIncludeEdliAdminInStructure(boolean includeEdliAdminInStructure) {
        this.includeEdliAdminInStructure = includeEdliAdminInStructure;
    }

    public boolean isAbryScheme() {
        return abryScheme;
    }

    public void setAbryScheme(boolean abryScheme) {
        this.abryScheme = abryScheme;
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
