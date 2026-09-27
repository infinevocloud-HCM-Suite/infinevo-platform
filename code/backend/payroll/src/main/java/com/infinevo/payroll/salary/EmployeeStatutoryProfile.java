package com.infinevo.payroll.salary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Employee statutory profile entity (W-26.2).
 */
@Entity
@Table(
        name = "employee_statutory_profile",
        schema = "payroll",
        indexes = {
            @Index(
                    name = "uk_employee_statutory_profile_tenant_employee",
                    columnList = "tenant_id, employee_id",
                    unique = true)
        })
public class EmployeeStatutoryProfile {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "is_eligible_for_pf", nullable = false)
    private boolean eligibleForPf = false;

    @Column(name = "is_eligible_for_pt", nullable = false)
    private boolean eligibleForPt = false;

    @Column(name = "is_eligible_for_lwf", nullable = false)
    private boolean eligibleForLwf = false;

    @Column(name = "is_eligible_for_esi", nullable = false)
    private boolean eligibleForEsi = false;

    @Column(name = "is_eligible_for_eps", nullable = false)
    private boolean eligibleForEps = false;

    @Column(name = "contributes_eps_on_higher_wages", nullable = false)
    private boolean contributesEpsOnHigherWages = false;

    @Column(name = "is_director", nullable = false)
    private boolean director = false;

    @Column(name = "pf_account_number", length = 32)
    private String pfAccountNumber;

    @Column(name = "uan", length = 16)
    private String uan;

    @Column(name = "esi_number", length = 32)
    private String esiNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected EmployeeStatutoryProfile() {}

    public EmployeeStatutoryProfile(UUID tenantId, UUID employeeId, String actor) {
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.createdBy = actor != null ? actor : ACTOR_SYSTEM;
        this.updatedBy = actor != null ? actor : ACTOR_SYSTEM;
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

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public boolean isEligibleForPf() {
        return eligibleForPf;
    }

    public void setEligibleForPf(boolean eligibleForPf) {
        this.eligibleForPf = eligibleForPf;
    }

    public boolean isEligibleForPt() {
        return eligibleForPt;
    }

    public void setEligibleForPt(boolean eligibleForPt) {
        this.eligibleForPt = eligibleForPt;
    }

    public boolean isEligibleForLwf() {
        return eligibleForLwf;
    }

    public void setEligibleForLwf(boolean eligibleForLwf) {
        this.eligibleForLwf = eligibleForLwf;
    }

    public boolean isEligibleForEsi() {
        return eligibleForEsi;
    }

    public void setEligibleForEsi(boolean eligibleForEsi) {
        this.eligibleForEsi = eligibleForEsi;
    }

    public boolean isEligibleForEps() {
        return eligibleForEps;
    }

    public void setEligibleForEps(boolean eligibleForEps) {
        this.eligibleForEps = eligibleForEps;
    }

    public boolean isContributesEpsOnHigherWages() {
        return contributesEpsOnHigherWages;
    }

    public void setContributesEpsOnHigherWages(boolean contributesEpsOnHigherWages) {
        this.contributesEpsOnHigherWages = contributesEpsOnHigherWages;
    }

    public boolean isDirector() {
        return director;
    }

    public void setDirector(boolean director) {
        this.director = director;
    }

    public String getPfAccountNumber() {
        return pfAccountNumber;
    }

    public void setPfAccountNumber(String pfAccountNumber) {
        this.pfAccountNumber = pfAccountNumber;
    }

    public String getUan() {
        return uan;
    }

    public void setUan(String uan) {
        this.uan = uan;
    }

    public String getEsiNumber() {
        return esiNumber;
    }

    public void setEsiNumber(String esiNumber) {
        this.esiNumber = esiNumber;
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
        this.updatedBy = updatedBy != null ? updatedBy : ACTOR_SYSTEM;
    }
}
