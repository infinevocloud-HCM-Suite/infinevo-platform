package com.infinevo.payroll.taxdeclaration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Tenant-level window settings for employee income tax declarations per financial year (W-32.1).
 */
@Entity
@Table(schema = "payroll", name = "income_tax_declaration")
public class IncomeTaxDeclarationWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "window_opens_on", nullable = false)
    private LocalDate windowOpensOn;

    @Column(name = "window_closes_on", nullable = false)
    private LocalDate windowClosesOn;

    @Column(name = "is_locked", nullable = false)
    private boolean isLocked = false;

    @Column(name = "default_tax_regime", nullable = false, length = 3)
    private String defaultTaxRegime = "NEW";

    @Column(name = "can_change_tax_regime", nullable = false)
    private boolean canChangeTaxRegime = true;

    @Column(name = "pan_required_for_rent_over_threshold", nullable = false)
    private boolean panRequiredForRentOverThreshold = true;

    @Column(name = "notify_on_lock", nullable = false)
    private boolean notifyOnLock = false;

    @Column(name = "notify_on_release", nullable = false)
    private boolean notifyOnRelease = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public IncomeTaxDeclarationWindow() {}

    public IncomeTaxDeclarationWindow(
            UUID tenantId,
            String financialYear,
            LocalDate windowOpensOn,
            LocalDate windowClosesOn,
            String defaultTaxRegime,
            boolean canChangeTaxRegime,
            boolean panRequiredForRentOverThreshold) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.financialYear = Objects.requireNonNull(financialYear, "financialYear must not be null");
        this.windowOpensOn = Objects.requireNonNull(windowOpensOn, "windowOpensOn must not be null");
        this.windowClosesOn = Objects.requireNonNull(windowClosesOn, "windowClosesOn must not be null");
        this.defaultTaxRegime = defaultTaxRegime != null ? defaultTaxRegime : "NEW";
        this.canChangeTaxRegime = canChangeTaxRegime;
        this.panRequiredForRentOverThreshold = panRequiredForRentOverThreshold;
    }

    public boolean isOpenOn(LocalDate date) {
        if (isLocked) {
            return false;
        }
        if (date == null || windowOpensOn == null || windowClosesOn == null) {
            return false;
        }
        return !date.isBefore(windowOpensOn) && !date.isAfter(windowClosesOn);
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

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public LocalDate getWindowOpensOn() {
        return windowOpensOn;
    }

    public void setWindowOpensOn(LocalDate windowOpensOn) {
        this.windowOpensOn = windowOpensOn;
    }

    public LocalDate getWindowClosesOn() {
        return windowClosesOn;
    }

    public void setWindowClosesOn(LocalDate windowClosesOn) {
        this.windowClosesOn = windowClosesOn;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    public String getDefaultTaxRegime() {
        return defaultTaxRegime;
    }

    public void setDefaultTaxRegime(String defaultTaxRegime) {
        this.defaultTaxRegime = defaultTaxRegime;
    }

    public boolean isCanChangeTaxRegime() {
        return canChangeTaxRegime;
    }

    public void setCanChangeTaxRegime(boolean canChangeTaxRegime) {
        this.canChangeTaxRegime = canChangeTaxRegime;
    }

    public boolean isPanRequiredForRentOverThreshold() {
        return panRequiredForRentOverThreshold;
    }

    public void setPanRequiredForRentOverThreshold(boolean panRequiredForRentOverThreshold) {
        this.panRequiredForRentOverThreshold = panRequiredForRentOverThreshold;
    }

    public boolean isNotifyOnLock() {
        return notifyOnLock;
    }

    public void setNotifyOnLock(boolean notifyOnLock) {
        this.notifyOnLock = notifyOnLock;
    }

    public boolean isNotifyOnRelease() {
        return notifyOnRelease;
    }

    public void setNotifyOnRelease(boolean notifyOnRelease) {
        this.notifyOnRelease = notifyOnRelease;
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
